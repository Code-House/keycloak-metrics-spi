package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.GaugeMetricFamily;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.management.Attribute;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.StringValueExp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinispanExports extends Collector {

    private static boolean initialized;

    private final static String[] cacheAttributes = new String[] {
         "activations",
         "averageReadTime",
         "averageRemoveTime",
         "averageReplicationTime",
         "averageWriteTime",
         "evictions",
         "hitRatio",
         "hits",
         "invalidations",
         "misses",
         "numberOfEntries",
         "numberOfEntriesInMemory",
         "passivations",
         "readWriteRatio",
         "removeHits",
         "removeMisses",
         "replicationCount",
         "replicationFailures",
         "successRatio",
         "timeSinceReset",
         "timeSinceStart",
         "writes"
    };
    private final static String[] localCacheAttributes = new String[] {
        "activations",
        "averageReadTime",
        "averageWriteTime",
        "elapsedTime",
        "hitRatio",
        "hits",
        "invalidations",
        "misses",
        "numberOfEntries",
        "passivations",
        "readWriteRatio",
        "removeHits",
        "removeMisses",
        "stores"
    };
    // jboss.as:subsystem=infinispan,cache-container=keycloak,local-cache=users,memory=object
    private final static String[] objectAttributes = new String[] {
        "evictions", "maxEntries", "size",
    };
    // jboss.as:subsystem=infinispan,cache-container=keycloak,local-cache=users,component=transaction
    private final static String[] txAttributes = new String[] {
        "commits", "prepares", "rollbacks",
    };

    private final Logger logger = LoggerFactory.getLogger(InfinispanExports.class);
    private final MBeanServerConnection connection;

    private InfinispanExports() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    private InfinispanExports(MBeanServerConnection connection) {
        this.connection = connection;
    }

    public static void initialize() {
        if (!initialized) {
            new InfinispanExports().register(CollectorRegistry.defaultRegistry);
            initialized = true;
        }
    }

    @Override
    public List<MetricFamilySamples> collect() {
        Map<CacheTypeKey, GaugeMetricFamily> families = new LinkedHashMap<>();
        for (String attribute : localCacheAttributes) {
            families.put(new CacheTypeKey("local", attribute), new GaugeMetricFamily(
                "infinispan_local_" + attribute,
                "Local cache object statistics",
                Arrays.asList("container", "cache")
            ));
        }
        for (String attribute : cacheAttributes) {
            families.put(new CacheTypeKey("", attribute), new GaugeMetricFamily(
                "infinispan_remote_" + attribute,
                "Local cache object statistics",
                Arrays.asList("container", "cache")
            ));
        }
        for (String attribute : objectAttributes) {
            families.put(new CacheTypeKey("object", attribute), new GaugeMetricFamily(
                "infinispan_local_statistic_" + attribute,
                "Object statistics",
                Arrays.asList("container", "cache")
            ));
        }
        for (String attribute : txAttributes) {
            families.put(new CacheTypeKey("tx", attribute), new GaugeMetricFamily(
                "infinispan_local_transaction_" + attribute,
                "Transaction statistics",
                Arrays.asList("container", "cache")
            ));
        }


        try {
            process(families, new ObjectName("jboss.as:subsystem=infinispan,cache-container=*,cache=*"), "", "cache", cacheAttributes);
            process(families, new ObjectName("jboss.as:subsystem=infinispan,cache-container=*,local-cache=*"), "local", "local-cache", localCacheAttributes);
            process(families, new ObjectName("jboss.as:subsystem=infinispan,cache-container=*,local-cache=*,memory=object"), "object", "local-cache", objectAttributes);
            process(families, new ObjectName("jboss.as:subsystem=infinispan,cache-container=*,local-cache=*,component=transaction"), "tx", "local-cache", txAttributes);
        } catch (Exception e) {
            logger.info("Could not collect metrics", e);
        }

        return new ArrayList<>(families.values());
    }

    private void process(Map<CacheTypeKey, GaugeMetricFamily> families, ObjectName objectName, String type, String cacheNameKey, String[] attributeNames)
        throws IOException, InstanceNotFoundException, ReflectionException {
        Set<ObjectName> objectNames = connection.queryNames(objectName, null);

        logger.info("Found objects {} matching query {}", objectNames.size(), objectName);

        for (ObjectName name : objectNames) {
            logger.info("Checking {}", name);

            String keyProperty = name.getKeyProperty(cacheNameKey);
            //  Below caches cause errors under KC 9
            if ("userRevisions".equals(keyProperty) || "realmRevisions".equals(keyProperty) || "authorizationRevisions".equals(keyProperty)) {
//                try {
//                    List<String> collect = Arrays
//                        .stream(connection.getMBeanInfo(name).getAttributes())
//                        .map(
//                            a -> a.getName() + "(" + a.getType() + ") " + a.getDescription() + "\n")
//                        .collect(Collectors.toList());
//                    logger.info("Ignoring problematic cache {}. Available attributes {}", name, collect);
//
//                    for (String att : attributeNames) {
//                        try {
//                            logger.info("Attribute {} of {}={}", new Object[] {att, name, connection.getAttribute(name, att)});
//                        } catch (Exception e) {
//                            logger.info("Failed to read {} of {}", new Object[] {att, name}, e);
//                        }
//                    }
//                } catch (IntrospectionException e) {
//                    e.printStackTrace();
//                }
                continue;
            }

            List<Attribute> attributes = connection.getAttributes(name, attributeNames).asList();

            for (Attribute attribute : attributes) {
                String attributeName = attribute.getName();
                Object value = attribute.getValue();
                if (value instanceof Number) {
                    GaugeMetricFamily gaugeMetricFamily = families.get(new CacheTypeKey(type, attributeName));
                    List<String> labelValues = Arrays.asList(name.getKeyProperty("cache-container"), keyProperty);
                    gaugeMetricFamily.addMetric(labelValues, ((Number) value).doubleValue());
                }
            }
        }
    }

    class CacheTypeKey {
        public String type;
        public String key;

        public CacheTypeKey(String type, String key) {
            this.type = type;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CacheTypeKey)) {
                return false;
            }
            CacheTypeKey that = (CacheTypeKey) o;
            return Objects.equals(type, that.type) && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, key);
        }
    }
}
