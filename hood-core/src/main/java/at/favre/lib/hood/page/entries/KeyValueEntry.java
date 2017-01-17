package at.favre.lib.hood.page.entries;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import at.favre.lib.hood.R;
import at.favre.lib.hood.interfaces.PageEntry;
import at.favre.lib.hood.interfaces.ViewTemplate;
import at.favre.lib.hood.interfaces.ViewTypes;
import at.favre.lib.hood.interfaces.actions.OnClickAction;
import at.favre.lib.hood.interfaces.values.DynamicValue;
import at.favre.lib.hood.util.PermissionTranslator;
import at.favre.lib.hood.util.defaults.DefaultMiscActions;
import at.favre.lib.hood.view.HoodDebugPageView;
import at.favre.lib.hood.view.KeyValueDetailDialogs;
import timber.log.Timber;

/**
 * An entry that has an key and value (e.g. normal properties). Supports custom click actions, multi line values
 * and dynamic values.
 */
public class KeyValueEntry implements Comparator<KeyValueEntry>, PageEntry<Map.Entry<CharSequence, KeyValueEntry.Value<String>>> {

    private Map.Entry<CharSequence, Value<String>> data;
    private final Template template;

    /**
     * Creates Key-Value style page entry.
     *
     * @param key        as shown in ui
     * @param value      dynamic value (e.g. from {@link android.content.SharedPreferences}
     * @param action     used when clicked on
     * @param multiLine  if a different layout should be used for long values
     * @param asyncValue if true will retrieve the value in a background thread and cache it until the view is refreshed
     */
    public KeyValueEntry(CharSequence key, DynamicValue<String> value, OnClickAction action, boolean multiLine, boolean asyncValue) {
        this.data = new AbstractMap.SimpleEntry<>(key, new Value<>(value, asyncValue));
        this.template = new Template(multiLine, action, value);
    }

    /**
     * Creates Key-Value style page entry.
     *
     * @param key       as shown in ui
     * @param value     dynamic value (e.g. from {@link android.content.SharedPreferences}
     * @param action    used when clicked on
     * @param multiLine if a different layout should be used for long values
     */
    public KeyValueEntry(CharSequence key, DynamicValue<String> value, OnClickAction action, boolean multiLine) {
        this(key, value, action, multiLine, false);
    }

    /**
     * Creates Key-Value style page entry. Uses dialog as default click action.
     *
     * @param key       as shown in ui
     * @param value     dynamic value (e.g. from {@link android.content.SharedPreferences}
     * @param multiLine if a different layout should be used for long values
     */
    public KeyValueEntry(CharSequence key, DynamicValue<String> value, boolean multiLine) {
        this(key, value, new DialogClickAction(), multiLine);
    }

    /**
     * Creates Key-Value style page entry. Uses dialog as default click action and is not
     * multiline enabled.
     *
     * @param key   as shown in ui
     * @param value dynamic value (e.g. from {@link android.content.SharedPreferences}
     */
    public KeyValueEntry(CharSequence key, DynamicValue<String> value) {
        this(key, value, new DialogClickAction(), false);
    }

    /**
     * Creates Key-Value style page entry with a static value.
     *
     * @param key       as shown in ui
     * @param value     static value
     * @param action    used when clicked on
     * @param multiLine if a different layout should be used for long values
     */
    public KeyValueEntry(CharSequence key, final String value, OnClickAction action, boolean multiLine) {
        this(key, new DynamicValue.DefaultStaticValue<>(value), action, multiLine);
    }

    /**
     * Creates Key-Value style page entry with a static value. Uses dialog as default click action.
     *
     * @param key       as shown in ui
     * @param value     static value
     * @param multiLine if a different layout should be used for long values
     */
    public KeyValueEntry(CharSequence key, final String value, boolean multiLine) {
        this(key, new DynamicValue.DefaultStaticValue<>(value), new DialogClickAction(), multiLine);
    }

    /**
     * Creates Key-Value style page entry with a static value. Uses dialog as default click action and
     * ist not multi-line enabled.
     *
     * @param key   as shown in ui
     * @param value static value
     */
    public KeyValueEntry(CharSequence key, final String value) {
        this(key, value, false);
    }

    @Override
    public Map.Entry<CharSequence, KeyValueEntry.Value<String>> getValue() {
        return data;
    }

    @Override
    public ViewTemplate<Map.Entry<CharSequence, KeyValueEntry.Value<String>>> getViewTemplate() {
        return template;
    }

    @Override
    public String toLogString() {
        return "\t" + data.getKey() + "=" + data.getValue();
    }

    @Override
    public void refresh() {
        data.getValue().refresh();
    }

    @Override
    public int compare(KeyValueEntry o1, KeyValueEntry o2) {
        return String.valueOf(o1.getValue().getKey()).compareTo(o2.getValue().getKey().toString());
    }

    private static class Template implements ViewTemplate<Map.Entry<CharSequence, KeyValueEntry.Value<String>>> {
        private final boolean multiLine;
        private OnClickAction clickAction;
        private final DynamicValue<String> dynamicValue;
        private ConcurrentHashMap<String, ValueBackgroundTask> taskMap = new ConcurrentHashMap<>();

        public Template(boolean multiLine, OnClickAction clickAction, DynamicValue<String> dynamicValue) {
            this.multiLine = multiLine;
            this.clickAction = clickAction;
            this.dynamicValue = dynamicValue;
        }

        @Override
        public int getViewType() {
            return multiLine ? ViewTypes.VIEWTYPE_KEYVALUE_MULTILINE : ViewTypes.VIEWTYPE_KEYVALUE;
        }

        @Override
        public View constructView(ViewGroup viewGroup, LayoutInflater inflater) {
            if (multiLine) {
                return inflater.inflate(R.layout.hoodlib_template_keyvalue_multiline, viewGroup, false);
            } else {
                return inflater.inflate(R.layout.hoodlib_template_keyvalue, viewGroup, false);
            }
        }

        @Override
        public void setContent(final Map.Entry<CharSequence, KeyValueEntry.Value<String>> entry, @NonNull final View view) {
            ((TextView) view.findViewById(R.id.key)).setText(entry.getKey());
            TextView tvValue = ((TextView) view.findViewById(R.id.value));

            tvValue.setTag(entry.getValue().id);
            if (entry.getValue().needsRefresh) {
                tvValue.setVisibility(View.GONE);
                view.findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);

                ValueBackgroundTask task;
                if (taskMap.containsKey(entry.getValue().id)) {
                    task = taskMap.get(entry.getValue().id);
                } else {
                    task = new ValueBackgroundTask(entry.getValue());
                    taskMap.put(entry.getValue().id, task);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }

                task.setCallback(new Runnable() {
                    @Override
                    public void run() {
                        taskMap.remove(entry.getValue().id);
                        setValueToView(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().cache), view, entry.getValue().id);
                    }
                });
            } else {
                setValueToView(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().cache), view, entry.getValue().id);
            }
        }

        private void setValueToView(final Map.Entry<CharSequence, String> entry, @NonNull final View view, final String tagId) {
            TextView tvValue = ((TextView) view.findViewById(R.id.value));
            if (tagId.equals(tvValue.getTag())) {
                view.findViewById(R.id.progress_bar).setVisibility(View.GONE);
                tvValue.setVisibility(View.VISIBLE);
                tvValue.setText(entry.getValue());
                if (clickAction != null) {
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            clickAction.onClick(v, entry);
                        }
                    });
                    view.setClickable(true);
                } else {
                    view.setOnClickListener(null);
                    view.setClickable(false);
                }
            }
        }

        @Override
        public void decorateViewWithZebra(@NonNull View view, @ColorInt int zebraColor, boolean isOdd) {
            HoodDebugPageView.setZebraToView(view, zebraColor, isOdd);
        }
    }

    public static class ValueBackgroundTask extends AsyncTask<Void, Void, String> {
        private Value<String> value;
        private Runnable callback;

        public ValueBackgroundTask(Value<String> value) {
            this.value = value;
        }

        public void setCallback(Runnable callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(Void... params) {
            return value.dynamicValue.getValue();
        }

        @Override
        protected void onPostExecute(String result) {
            value.setProcessedValue(result);
            callback.run();
        }
    }

    /**
     * Wrapper for a value that can be refreshed in the background
     *
     * @param <T>
     */
    public static class Value<T> {
        final String id;
        T cache;
        final DynamicValue<T> dynamicValue;
        final boolean processInBackground;
        boolean needsRefresh;

        public Value(DynamicValue<T> dynamicValue, boolean processInBackground) {
            this.id = UUID.randomUUID().toString();
            this.processInBackground = processInBackground;
            this.dynamicValue = dynamicValue;
            this.needsRefresh = processInBackground;
            if (!processInBackground) {
                cache = dynamicValue.getValue();
            }
        }

        public void setProcessedValue(T processedValue) {
            cache = processedValue;
            needsRefresh = false;
        }

        public void refresh() {
            if (processInBackground) {
                cache = null;
                needsRefresh = true;
            } else {
                cache = dynamicValue.getValue();
                needsRefresh = false;
            }
        }
    }

    /**
     * Use this to provide a additional long label used in e.g. default dialogs
     */
    public static class Label implements CharSequence, Comparable<Label> {
        public final CharSequence label;
        public final CharSequence longLabel;

        public Label(@NonNull CharSequence label) {
            this(label, label);
        }

        public Label(@NonNull CharSequence label, @NonNull CharSequence longLabel) {
            this.label = label;
            this.longLabel = longLabel;
        }

        @Override
        public int length() {
            return label.length();
        }

        @Override
        public char charAt(int i) {
            return label.charAt(i);
        }

        @Override
        public CharSequence subSequence(int i, int i1) {
            return label.subSequence(i, i1);
        }

        @NonNull
        @Override
        public String toString() {
            return label.toString();
        }

        @Override
        public int compareTo(@NonNull Label label) {
            return this.label.toString().compareTo(label.label.toString());
        }
    }

    private static CharSequence getLongLabel(CharSequence c) {
        if (c instanceof Label) {
            return ((Label) c).longLabel;
        }
        return c;
    }

    /* *************************************************************************** ONCLICKACTIONS */

    /**
     * Shows a simple toast with key/value
     */
    public static class ToastClickAction implements OnClickAction {
        @Override
        public void onClick(View v, Map.Entry<CharSequence, String> value) {
            Toast.makeText(v.getContext(), getLongLabel(value.getKey()) + "\n" + value.getValue(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Starts the defined runtime permission check on click or shows current status if grandted
     */
    public static class AskPermissionClickAction implements OnClickAction {
        private final String androidPermissionName;
        private final Activity activity;

        public AskPermissionClickAction(String androidPermissionName, Activity activity) {
            this.androidPermissionName = androidPermissionName;
            this.activity = activity;
        }

        @Override
        public void onClick(View v, Map.Entry<CharSequence, String> value) {
            Timber.d("check android permissions for " + androidPermissionName);
            @PermissionTranslator.PermissionState int permissionState = PermissionTranslator.getPermissionStatus(activity, androidPermissionName);

            if (permissionState == PermissionTranslator.GRANTED) {
                Toast.makeText(activity, R.string.hood_toast_already_allowed, Toast.LENGTH_SHORT).show();
                v.getContext().startActivity(DefaultMiscActions.getAppInfoIntent(v.getContext()));
            } else if (permissionState == PermissionTranslator.GRANTED_ON_INSTALL) {
                KeyValueDetailDialogs.DialogFragmentWrapper.newInstance(getLongLabel(value.getKey()), value.getValue())
                        .show(((Activity) v.getContext()).getFragmentManager(), String.valueOf(value.getKey()));
            } else {
                Timber.d("permission " + androidPermissionName + " not granted yet, show dialog");
                ActivityCompat.requestPermissions(activity, new String[]{androidPermissionName}, 2587);
            }
        }
    }

    /**
     * An click action that shows key/value in a dialog
     */
    public static class DialogClickAction implements OnClickAction {

        @Override
        public void onClick(View v, Map.Entry<CharSequence, String> value) {
            if (v.getContext() instanceof Activity) {
                KeyValueDetailDialogs.DialogFragmentWrapper.newInstance(getLongLabel(value.getKey()), value.getValue())
                        .show(((Activity) v.getContext()).getFragmentManager(), String.valueOf(value.getKey()));
            } else {
                new KeyValueDetailDialogs.CustomDialog(v.getContext(), getLongLabel(value.getKey()), value.getValue(), null).show();
            }
        }
    }

    /**
     * An click action that starts an {@link Intent}
     */
    public static class StartIntentAction implements OnClickAction {
        private final Intent intent;

        public StartIntentAction(Intent intent) {
            this.intent = intent;
        }

        @Override
        public void onClick(View v, Map.Entry<CharSequence, String> value) {
            v.getContext().startActivity(intent);
        }
    }
}
