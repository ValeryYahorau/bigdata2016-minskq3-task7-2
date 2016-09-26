package com.epam.bigdata2016.minskq3.task7;

import org.apache.commons.lang.StringUtils;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TagsInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TagsInterceptor.class);

    private static final String USER_TAGS_DICTIONARY = "/tmp/user.profile.tags.us.txt";
    private static final String HDFS_ROOT_PATH = "hdfs://sandbox.hortonworks.com:8020";

    private Map<String, String> tagsDictionary;

    @Override
    public void initialize() {
        log.info("TagsInterceptor initialization is starting.");
        prepareTagsDictionary();
    }

    @Override
    public Event intercept(Event event) {
        Map<String, String> headers = event.getHeaders();

        String eventBodyStr = new String(event.getBody());
        List<String> params = new ArrayList<>(Arrays.asList(eventBodyStr.split("\\t")));


        String eventDate = params.get(1).substring(0, 8);
        headers.put("event_date", eventDate);

        String userTagsId = params.get(20);
        String userTags = tagsDictionary.getOrDefault(userTagsId, "");
        headers.put("tags_added", StringUtils.isNotBlank(userTags) ? "true" : "false");
        params.add(userTags);

        event.setHeaders(headers);
        event.setBody(String.join("\t", params).getBytes());
        return event;
    }

    @Override
    public java.util.List<Event> intercept(java.util.List<Event> events) {
        List<Event> interceptedEvents = new ArrayList<Event>(events.size());
        for (Event event : events) {
            Event interceptedEvent = intercept(event);
            interceptedEvents.add(interceptedEvent);
        }
        return interceptedEvents;
    }

    @Override
    public void close() {
        log.info("TagsInterceptor closing.");
    }

    public static class Builder implements Interceptor.Builder {

        @Override
        public void configure(Context context) {
        }

        @Override
        public Interceptor build() {
            return new TagsInterceptor();
        }
    }

    private void prepareTagsDictionary() {
        try {
            Configuration config = new Configuration();
            FileSystem fs = FileSystem.get(new URI(HDFS_ROOT_PATH), config);
            Path path = new Path(USER_TAGS_DICTIONARY);
            BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(path)));

            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            this.tagsDictionary = lines.stream()
                    .skip(1)
                    .map(s -> s.split("\\t"))
                    .collect(Collectors.toMap(
                            row -> row[0],
                            row -> row[1]
                    ));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}