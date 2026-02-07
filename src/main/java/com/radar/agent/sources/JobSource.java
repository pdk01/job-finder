package com.radar.agent.sources;

import com.radar.agent.model.Job;

import java.time.Instant;
import java.util.List;

public interface JobSource {
    String name();
    List<Job> fetch(Instant cutoff);
}