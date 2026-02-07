package com.radar.agent.pipeline;

import com.radar.agent.Config;
import com.radar.agent.model.Job;
import com.radar.agent.sources.AdzunaSource;
import com.radar.agent.sources.JobSource;
import com.radar.agent.sources.RemotiveSource;
import com.radar.agent.sources.RemoteOkSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SourcePipeline {
    public List<Job> fetch(Config config, Instant cutoff) {
        List<JobSource> sources = buildSources(config);
        List<Job> jobs = new ArrayList<>();
        for (JobSource source : sources) {
            jobs.addAll(source.fetch(cutoff));
        }
        return jobs;
    }

    private List<JobSource> buildSources(Config config) {
        List<JobSource> sources = new ArrayList<>();
        for (Config.SourceConfig source : config.sources) {
            if (source.type == null) {
                continue;
            }
            switch (source.type.toLowerCase(Locale.ROOT)) {
                case "remotive" -> sources.add(new RemotiveSource());
                case "remoteok" -> sources.add(new RemoteOkSource());
                case "adzuna" -> sources.add(new AdzunaSource(config.adzuna));
                default -> System.err.println("Unknown source type: " + source.type);
            }
        }
        if (sources.isEmpty()) {
            sources.add(new RemotiveSource());
            sources.add(new RemoteOkSource());
            sources.add(new AdzunaSource(config.adzuna));
        }
        return sources;
    }
}
