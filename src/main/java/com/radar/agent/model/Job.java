package com.radar.agent.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Job {
    public String id;
    public String source;
    public String company;
    public String title;
    public String url;
    public String description;
    public Instant postedAt;

    public double score = -1;
    public double semanticScore = -1;
    public double qualityScore = -1;

    public String summary;
    public String qualityNotes;
    public String companyBrief;
    public String resumeGaps;
    public String resumeSuggestions;
    public String outreachMessage;
    public List<String> normalizedSkills = new ArrayList<>();

    public String triageLabel;
    public String triageReason;
    public String atsFindings;
    public String atsSuggestions;
    public String resumeVariantA;
    public String resumeVariantB;
    public String redFlags;
    public String portfolioBlurb;
    public String multiAgentNotes;

    public List<String> contactEmails = new ArrayList<>();
    public List<String> contactPhones = new ArrayList<>();
    public List<String> contactNames = new ArrayList<>();
    public List<String> contactGuesses = new ArrayList<>();

    public String coverLetterPath;
    public List<String> coverLetterVariants = new ArrayList<>();

    public Job() {}
}
