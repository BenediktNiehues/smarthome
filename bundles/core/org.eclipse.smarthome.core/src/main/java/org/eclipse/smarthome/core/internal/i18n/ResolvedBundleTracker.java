package org.eclipse.smarthome.core.internal.i18n;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.util.tracker.BundleTracker;


/**
 * The {@link ResolvedBundleTracker} tracks any bundles which have reached the "resolved" state
 * semantically. This means that not only a "started" or "starting" bundle is tracked but also
 * a bundle who has just reached the "resolved" state.
 * <p>
 * Override the methods {@link #addingBundle(Bundle)} or {@link #removedBundle(Bundle)}
 * to consume the events.
 * <p>
 * This class is a simple replacement for an <i>OSGi</i> {@link BundleTracker} whose usage
 * for monitoring semantic states is more complex. 
 * 
 * @author Michael Grammling - Initial Contribution
 */
public abstract class ResolvedBundleTracker implements BundleListener {

    private BundleContext bundleContext;
    private List<Bundle> trackedBundles;


    /**
     * Creates a new instance of this class with the specified parameter.
     * 
     * @param bundleContext a bundle context to be used to track any bundles (must not be null)
     * @throws IllegalArgumentException if the bundle context is null
     */
    public ResolvedBundleTracker(BundleContext bundleContext) throws IllegalArgumentException {
        if (bundleContext == null) {
            throw new IllegalArgumentException("The bundle context must not be null!");
        }

        this.bundleContext = bundleContext;
        this.trackedBundles = new ArrayList<>();
    }

    /**
     * Opens the tracker.
     * <p>
     * For each bundle which is already available and which has reached at least the "resolved"
     * state, a {@link #addingBundle(Bundle)} event is fired.
     */
    public synchronized void open() {
        this.bundleContext.addBundleListener(this);
        initialize();
    }

    /**
     * Closes the tracker.
     * <p>
     * For each tracked bundle a {@link #removedBundle(Bundle)} event is fired.
     */
    public synchronized void close() {
        this.bundleContext.removeBundleListener(this);
        uninitialize();
    }

    private synchronized void initialize() {
        Bundle[] bundles = this.bundleContext.getBundles();
        for (Bundle bundle : bundles) {
            int state = bundle.getState();
            if ((state & (Bundle.INSTALLED | Bundle.UNINSTALLED)) > 0) {
                remove(bundle);
            } else {
                add(bundle);
            }
        }
    }

    private synchronized void uninitialize() {
        for (Bundle bundle : this.trackedBundles) {
            try {
                removedBundle(bundle);
            } catch (Exception ex) {
                // nothing to do
            }
        }

        this.trackedBundles.clear();
    }

    @Override
    public synchronized void bundleChanged(BundleEvent event) {
        Bundle bundle = event.getBundle();
        int type = event.getType();

        if ((type & ((BundleEvent.STARTING | BundleEvent.STARTED | BundleEvent.RESOLVED))) > 0) {
            add(bundle);
        } else if ((type & (BundleEvent.UNINSTALLED | BundleEvent.UNRESOLVED)) > 0) {
            remove(bundle);
        }
    }

    private boolean isThisBundle(Bundle bundle) {
        return (this.bundleContext.getBundle() == bundle);
    }

    private void add(Bundle bundle) {
        if ((!isThisBundle(bundle) && (!this.trackedBundles.contains(bundle)))) {
            try {
                if (addingBundle(bundle)) {
                    this.trackedBundles.add(bundle);
                }
            } catch (Exception ex) {
                // nothing to do
            }
        }
    }

    private void remove(Bundle bundle) {
        if (this.trackedBundles.contains(bundle)) {
            try {
                removedBundle(bundle);
            } catch (Exception ex) {
                // nothing to do
            } finally {
                this.trackedBundles.remove(bundle);
            }
        }
    }

    /**
     * The callback method to be invoked when a bundle was detected which at least reached
     * the "resolved" state.
     * <p>
     * This method might be overridden.
     * 
     * @param bundle the according bundle (not null)
     * @return true  if the bundle should be tracked, otherwise false
     */
    public boolean addingBundle(Bundle bundle) {
        // override this method if needed
        return false;
    }

    /**
     * The callback method to be invoked when a tracked bundle was detected which has left at
     * least the "resolved" state semantically (e.g. if it was "uninstalled", etc.).
     * <p>
     * This method might be overridden.
     * 
     * @param bundle the according bundle (not null)
     */
    public void removedBundle(Bundle bundle) {
        // override this method if needed
    }

}
