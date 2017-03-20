package org.eclipse.smarthome.automation.module.core.handler;

import java.math.BigDecimal;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.handler.BaseTriggerModuleHandler;
import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.events.Event;
import org.eclipse.smarthome.core.events.EventFilter;
import org.eclipse.smarthome.core.events.EventSubscriber;
import org.eclipse.smarthome.core.items.events.ItemStateChangedEvent;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.TypeParser;
import org.eclipse.smarthome.core.types.UnDefType;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class ItemStateThresholdTriggerHandler extends BaseTriggerModuleHandler implements EventSubscriber, EventFilter {

    Logger logger = LoggerFactory.getLogger(ItemStateThresholdTriggerHandler.class);

    public static final String MODULE_TYPE_ID = "core.ItemStateThresholdTrigger";

    private static final String CFG_ITEM_NAME = "itemName";
    private static final String CFG_TIMEBOX_VALUE = "timeboxValue";
    /**
     * MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS
     */
    private static final String CFG_TIMEBOX_UNIT = "timeboxUnit";
    private static final String CFG_OPERATOR = "operator";
    private static final String CFG_VALUE = "threshold";

    private static final Set<String> EVENT_TYPES = ImmutableSet.of(ItemStateChangedEvent.TYPE);

    private String itemName;
    private Long timeboxValue = null;
    private TimeUnit timeboxUnit;
    private CompareOperators operator;
    private String compareValue;

    private ScheduledExecutorService executorService = ThreadPoolManager
            .getScheduledPool(ItemStateThresholdTriggerHandler.class.getSimpleName());
    private ScheduledFuture<Void> timeboxCallback;

    private State oldState = UnDefType.UNDEF;
    private State latestState = UnDefType.UNDEF;

    @SuppressWarnings("rawtypes")
    private ServiceRegistration eventSubscriberRegistration;

    private BundleContext bundleContext;

    public ItemStateThresholdTriggerHandler(Trigger module, BundleContext context) {
        super(module);
        this.bundleContext = context;
        initialize();
    }

    private void initialize() {
        itemName = (String) module.getConfiguration().get(CFG_ITEM_NAME);
        timeboxValue = (module.getConfiguration().get(CFG_TIMEBOX_VALUE) == null) ? null
                : ((BigDecimal) module.getConfiguration().get(CFG_TIMEBOX_VALUE)).longValue();
        timeboxUnit = TimeUnit.valueOf((String) module.getConfiguration().get(CFG_TIMEBOX_UNIT));
        operator = CompareOperators.valueOf((String) module.getConfiguration().get(CFG_OPERATOR));
        compareValue = (String) module.getConfiguration().get(CFG_VALUE);
        registerEventSubscriber();
    }

    private void registerEventSubscriber() {
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("event.topics", "smarthome/items/" + itemName + "/*");
        eventSubscriberRegistration = this.bundleContext.registerService(EventSubscriber.class.getName(), this,
                properties);
    }

    /**
     * do the cleanup: unregistering eventSubscriber...
     */
    @Override
    public void dispose() {
        super.dispose();
        if (eventSubscriberRegistration != null) {
            eventSubscriberRegistration.unregister();
            eventSubscriberRegistration = null;
        }
        if (timeboxCallback != null) {
            timeboxCallback.cancel(true);
            timeboxCallback = null;
        }
    }

    @Override
    public boolean apply(Event event) {
        return event.getTopic().contains(itemName);
    }

    @Override
    public Set<String> getSubscribedEventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public EventFilter getEventFilter() {
        return this;
    }

    @Override
    public void receive(Event event) {
        if (event instanceof ItemStateChangedEvent) {
            ItemStateChangedEvent itemEvent = (ItemStateChangedEvent) event;
            this.latestState = itemEvent.getItemState();
            this.oldState = itemEvent.getOldItemState();
            if (matches(latestState) && !matches(oldState)) {
                if (timeboxValue != null && (this.timeboxCallback == null || this.timeboxCallback.isDone()
                        || this.timeboxCallback.isCancelled())) {
                    this.timeboxCallback = executorService.schedule(new TimeboxCallback(), timeboxValue, timeboxUnit);
                } else {
                    triggerRule(itemEvent.getItemState());
                }
            } else if (matches(latestState) && matches(oldState) && timeboxValue != null
                    && (this.timeboxCallback == null || this.timeboxCallback.isDone()
                            || this.timeboxCallback.isCancelled())) {
                this.timeboxCallback = executorService.schedule(new TimeboxCallback(), timeboxValue,
                        TimeUnit.MILLISECONDS);
            } else {
                if (this.timeboxCallback != null) {
                    this.timeboxCallback.cancel(true);
                    this.timeboxCallback = null;
                }
            }
        }

    }

    private void triggerRule(State itemState) {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("itemState", itemState);
        if (this.timeboxValue != null) {
            outputs.put("timeboxValue", timeboxValue);
            outputs.put("timeboxUnit", timeboxUnit);
        }
        logger.trace("triggering rule");
        this.ruleEngineCallback.triggered(module, outputs);
    }

    private State getValue(String s, Class<? extends State> clzz) {
        return TypeParser.parseState(ImmutableList.of(clzz), s);
    }

    private boolean matches(State state) {
        if (this.operator == CompareOperators.BETWEEN) {
            return checkRange(state);
        }
        State toCompare = getValue(compareValue, state.getClass());
        Integer result = compare(state, toCompare);
        switch (this.operator) {
            case EQUALS:
                return state.equals(toCompare);
            case NOTEQUAL:
                return !state.equals(toCompare);
            case GT:
                return result == null ? false : result > 0;
            case GT_EQ:
                return result == null ? false : result >= 0;
            case LT:
                return result == null ? false : result < 0;
            case LT_EQ:
                return result == null ? false : result <= 0;
            case BETWEEN:
                // already branched before
            default:
                break;
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Integer compare(Object a, Object b) {
        if(a==null && b== null){
            return 0;
        }
        if (a==null||b==null){
            return null;
        }
        if (Comparable.class.isAssignableFrom(a.getClass()) && a.getClass().equals(b.getClass())) {
            try {
                return ((Comparable) a).compareTo(b);
            } catch (ClassCastException e) {
                // should never happen but to be save here!
                return null;
            }
        }
        return null;
    }

    private boolean checkRange(State state) {
        String[] range = compareValue.split(",");
        if (range.length == 2) {
            State minValue = getValue(range[0], state.getClass());
            State maxValue = getValue(range[1], state.getClass());
            if (minValue == null && maxValue == null) {
                return false;
            }
            if (compare(state, minValue) > 0 && compare(state, maxValue) < 0) {
                return true;
            }
        }
        return false;
    }

    private class TimeboxCallback implements Callable<Void> {

        @Override
        public Void call() throws Exception {
            logger.debug("timebox callback called");
            if (matches(latestState) && !matches(oldState)) {
                triggerRule(latestState);
            }
            return null;
        }

    }
}
