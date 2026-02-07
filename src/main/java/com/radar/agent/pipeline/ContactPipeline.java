package com.radar.agent.pipeline;

import com.radar.agent.Config;
import com.radar.agent.model.Job;
import com.radar.agent.util.ContactFinder;

import java.util.ArrayList;
import java.util.List;

public class ContactPipeline {
    public void enrichContacts(Config config, List<Job> jobs) {
        if (!config.openai.enableContactDiscovery) {
            return;
        }
        ContactFinder finder = new ContactFinder();
        int count = 0;
        for (Job job : jobs) {
            if (count >= config.maxContactLookups) {
                break;
            }
            ContactFinder.ContactResult contacts = finder.find(job, config.openai.enablePublicContactSearch);
            job.contactEmails = new ArrayList<>(contacts.emails);
            job.contactPhones = new ArrayList<>(contacts.phones);
            job.contactNames = new ArrayList<>(contacts.names);
            job.contactGuesses = new ArrayList<>(contacts.guessedEmails);
            count++;
        }
    }
}