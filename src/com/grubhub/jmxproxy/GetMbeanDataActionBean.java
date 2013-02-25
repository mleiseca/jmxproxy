package com.grubhub.jmxproxy;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * User: mleiseca
 * Date: 2/25/13
 * Time: 1:03 PM
 */
@UrlBinding("/mbean.action")
public class GetMbeanDataActionBean implements ActionBean{
    private static final Log log = LogFactory.getLog(GetMbeanDataActionBean.class);

    static final ConcurrentHashMap<String, JMXConnector> currentConnections = new ConcurrentHashMap<String, JMXConnector>();
    static final ConcurrentHashMap<String, Semaphore> connectionSemaphores = new ConcurrentHashMap<String, Semaphore>();

    String mBeanString;
    String methodName;
    String host;
    String port;

    public String getmBeanString() {
        return mBeanString;
    }

    public void setmBeanString(String mBeanString) {
        this.mBeanString = mBeanString;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    @DefaultHandler
    public Resolution fetchJmxData() throws Exception{

        String hostAndPort = host + ":" + port;

        Semaphore s = connectionSemaphores.putIfAbsent(hostAndPort, new Semaphore(1));

        try {
            s.acquire();

            MBeanServerConnection mbsc = getConnectionForHostAndPort(hostAndPort);

            log.debug("Using mBean: '" + mBeanString + "' and method name: " + methodName);
            ObjectName mxbeanName = new ObjectName (mBeanString);

            return new StreamingResolution("text/plain", getOutput(mbsc, mxbeanName));
        } finally {
            s.release();
        }
    }

    private String getOutput(MBeanServerConnection mbsc, ObjectName mxbeanName) throws Exception{

        if ( methodName.startsWith("get") ) {
            String attributeName = methodName.substring(3);
            return mbsc.getAttribute( mxbeanName, attributeName).toString();
        }else{
            return mbsc.invoke(mxbeanName, methodName, new Object[0], null).toString();
        }
    }

    private MBeanServerConnection getConnectionForHostAndPort(String hostAndPort) throws Exception  {


        JMXConnector jmxConnector = currentConnections.get(hostAndPort);
        try {
            if(jmxConnector != null){
                log.info("Using cached connection to: "+ hostAndPort);
                return jmxConnector.getMBeanServerConnection();
            }
        } catch (IOException e) {
            log.warn("Unable to use cached connection to " + hostAndPort, e);
        }

        JMXConnector jmxc = null;
        MBeanServerConnection mbsc = null;

        String serviceURL = "service:jmx:rmi:///jndi/rmi://" + hostAndPort + "/jmxrmi";

        JMXServiceURL url = new JMXServiceURL(serviceURL);
        log.info("Connecting to jmx at: " + serviceURL);
        jmxc = connectWithTimeout(url, 3 , TimeUnit.SECONDS);
        currentConnections.put(hostAndPort, jmxc);
        return jmxc.getMBeanServerConnection();
    }

    public static JMXConnector connectWithTimeout(
            final JMXServiceURL url, long timeout, TimeUnit unit)
            throws IOException {
        final BlockingQueue<Object> mailbox = new ArrayBlockingQueue<Object>(1);
        ExecutorService executor =
                Executors.newSingleThreadExecutor(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thr = new Thread(r);
                        thr.setDaemon(true);
                        return thr;
                    }
                });


        executor.submit(new Runnable() {
            public void run() {
                try {
                    JMXConnector connector = JMXConnectorFactory.connect(url);
                    if (!mailbox.offer(connector))
                        connector.close();
                } catch (Throwable t) {
                    mailbox.offer(t);
                }
            }
        });
        Object result;
        try {
            result = mailbox.poll(timeout, unit);
            if (result == null) {
                if (!mailbox.offer(""))
                    result = mailbox.take();
            }
        } catch (InterruptedException e) {
            throw initCause(new InterruptedIOException(e.getMessage()), e);
        } finally {
            executor.shutdown();
        }
        if (result == null)
            throw new SocketTimeoutException("Connect timed out: " + url);
        if (result instanceof JMXConnector)
            return (JMXConnector) result;
        try {
            throw (Throwable) result;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Throwable e) {
            // In principle this can't happen but we wrap it anyway
            throw new IOException(e.toString(), e);
        }
    }

    private static <T extends Throwable> T initCause(T wrapper, Throwable wrapped) {
        wrapper.initCause(wrapped);
        return wrapper;
    }


    ActionBeanContext actionBeanContext;
    @Override
    public void setContext(ActionBeanContext actionBeanContext) {
        this.actionBeanContext = actionBeanContext;
    }

    @Override
    public ActionBeanContext getContext() {
        return actionBeanContext;
    }
}
