package com.gathin.tvpowerhub;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";

    public interface Listener {
        void onIpChanged(String ipOrNull);
    }

    private final ConnectivityManager cm;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile String lastIp = null;
    private final Runnable evaluateRunnable = this::evaluateNow;

    public NetworkMonitor(Context context) {
        this.cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public synchronized void start() {
        if (networkCallback != null) return;
        try {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    schedule();
                }

                @Override
                public void onLost(Network network) {
                    schedule();
                }

                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                    schedule();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    schedule();
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
            schedule();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register default network callback: " + t.getMessage());
            evaluateNow();
        }
    }

    public synchronized void stop() {
        if (networkCallback != null) {
            try {
                cm.unregisterNetworkCallback(networkCallback);
            } catch (Throwable ignored) {}
            networkCallback = null;
        }
        mainHandler.removeCallbacks(evaluateRunnable);
    }

    public void schedule() {
        mainHandler.removeCallbacks(evaluateRunnable);
        mainHandler.postDelayed(evaluateRunnable, 400); // 400ms debounce
    }

    public String getCurrentIp() {
        if (lastIp == null) {
            lastIp = readIpv4();
        }
        return lastIp;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void evaluateNow() {
        String current = readIpv4();
        if (!Objects.equals(current, lastIp)) {
            Log.i(TAG, "IP changed: " + lastIp + " -> " + current);
            lastIp = current;
            for (Listener l : listeners) {
                try {
                    l.onIpChanged(current);
                } catch (Throwable t) {
                    Log.w(TAG, "Listener error: " + t.getMessage());
                }
            }
        }
    }

    /**
     * Reads the current active IPv4 address.
     * Priority: Ethernet first, then Wi-Fi.
     * Excludes VPN, cellular, loopback, and link-local (169.254.x.x) addresses.
     */
    public String readIpv4() {
        if (cm == null) return null;
        String wifiIp = null;
        try {
            Network[] networks = cm.getAllNetworks();
            if (networks != null) {
                for (Network n : networks) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    LinkProperties lp = cm.getLinkProperties(n);
                    if (nc == null || lp == null) continue;
                    if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;

                    boolean isEthernet = nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                    boolean isWifi = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    if (!isEthernet && !isWifi) continue;

                    for (LinkAddress la : lp.getLinkAddresses()) {
                        InetAddress addr = la.getAddress();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            String host = addr.getHostAddress();
                            if (isEthernet) {
                                return host; // Ethernet prioritized
                            }
                            if (wifiIp == null) {
                                wifiIp = host;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "readIpv4 error: " + t.getMessage());
        }
        return wifiIp;
    }
}
