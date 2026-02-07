package com.radar.agent.pipeline;

import com.radar.agent.model.Job;
import com.radar.agent.render.SiteRenderer;

import java.nio.file.Path;
import java.util.List;

public class OutputPipeline {
    public void write(Path outputDir, List<Job> jobs) {
        new SiteRenderer().writeSite(outputDir, jobs);
    }
}