package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import java.io.IOException;
import java.io.Writer;
import java.util.Enumeration;

public class CsvFormat {

    public final static String CONTENT_TYPE_001 = "text/csv; version=0.0.1; charset=utf-8";

    public static void write001(Writer writer, Enumeration<MetricFamilySamples> mfs) throws IOException {
        /* See http://prometheus.io/docs/instrumenting/exposition_formats/
         * for the output format specification. */
        //writer.write("type,sample,value,timestamp,labels\n");
        while(mfs.hasMoreElements()) {
            Collector.MetricFamilySamples metricFamilySamples = mfs.nextElement();
            for (Collector.MetricFamilySamples.Sample sample: metricFamilySamples.samples) {
                //writer.write(typeString(metricFamilySamples.type));
                //writer.write(',');
                //writer.write(metricFamilySamples.name);
                //writer.write(',');
                writer.write(sample.name);
                //writer.write(',');
                //if (sample.timestampMs != null){
                //    writer.write(sample.timestampMs.toString());
               // }
                writer.write('{');
                if (sample.labelNames.size() > 0) {
                    for (int i = 0; i < sample.labelNames.size(); ++i) {
                        writer.write(sample.labelNames.get(i));
                        writer.write("=\"");
                        writeEscapedLabelValue(writer, sample.labelValues.get(i));
                        writer.write("\",");
                    }
                }
                writer.write('}');
                writer.write(' ');
                writer.write(Collector.doubleToGoString(sample.value));
                writer.write('\n');
            }
        }
    }

    private static void writeEscapedHelp(Writer writer, String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    writer.append("\\\\");
                    break;
                case '\n':
                    writer.append("\\n");
                    break;
                default:
                    writer.append(c);
            }
        }
    }

    private static void writeEscapedLabelValue(Writer writer, String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    writer.append("\\\\");
                    break;
                case '\"':
                    writer.append("\\\"");
                    break;
                case '\n':
                    writer.append("\\n");
                    break;
                default:
                    writer.append(c);
            }
        }
    }

    private static String typeString(Collector.Type t) {
        switch (t) {
            case GAUGE:
                return "gauge";
            case COUNTER:
                return "counter";
            case SUMMARY:
                return "summary";
            case HISTOGRAM:
                return "histogram";
            default:
                return "untyped";
        }
    }

}
