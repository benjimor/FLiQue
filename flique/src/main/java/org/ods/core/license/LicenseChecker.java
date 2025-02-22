package org.ods.core.license;

import com.fluidops.fedx.EndpointManager;
import com.fluidops.fedx.algebra.StatementSource;
import com.fluidops.fedx.optimizer.SourceSelection;
import org.apache.jena.rdf.model.*;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class LicenseChecker {
    protected static final Logger log = LoggerFactory.getLogger(LicenseChecker.class);
    public Model summary;
    public HashMap<String, String> sourceLicenses;
    public Set<String> licenses;
    public HashMap<String, Integer> endpointLicenseConflicts;
    public HashMap<String, Integer> licenseConflicts;


    /**
     * Constructor
     *
     * @param licensedSummary FLiQuE summary
     */
    public LicenseChecker(Model licensedSummary) {
        this.summary = licensedSummary;
        this.sourceLicenses = new HashMap<>();
        this.licenses = new HashSet<>();
        this.endpointLicenseConflicts = new HashMap<>();
        this.licenseConflicts = new HashMap<>();
    }

    /**
     * Returns sources and its number of license conflicts
     * */
    public HashMap<String, Integer> getEndpointlicenseConflicts() {
        for (String license1: this.licenses) {
            int conflicts = 0;
            for (String license2: this.licenses) {
                if (!license1.equals(license2) && getCompliantLicenses(license1, license2).isEmpty()){
                    log.info("Conflict detected between license " + getLabelLicenses(license1) + " and license " + getLabelLicenses(license2));
                    conflicts++;
                }
            }
            this.licenseConflicts.put(license1, conflicts);
        }
        for (Map.Entry<String, String> entry : this.sourceLicenses.entrySet()) {
            String source = entry.getKey();
            String license = entry.getValue();
            this.endpointLicenseConflicts.put(source, this.licenseConflicts.get(license));
        }
        return this.endpointLicenseConflicts;
    }

    /**
     * Returns a set of licenses that can protect the result of the query
     * */
    public Set<String> getConsistentLicenses(SourceSelection sourceSelection, EndpointManager endpointManager) {
        Map<StatementPattern, List<StatementSource>> stmtToSources = sourceSelection.getStmtToSources();
        stmtToSources.forEach((stmt, sources) -> {
            sources.forEach(source -> {
                String endpointURL = endpointManager.getEndpoint(source.getEndpointID()).getEndpoint();
                String license = getLicense(endpointURL);
                this.sourceLicenses.put(endpointURL, license);
                this.licenses.add(license);
            });
        });
        return getCompliantLicenses(this.licenses);
    }

    private String getLicense(String endpoint) {
        Resource license = (Resource) this.summary.listObjectsOfProperty(ResourceFactory.createResource(endpoint), ResourceFactory.createProperty("http://www.w3.org/ns/odrl/2/hasPolicy")).next();
        return license.getURI();
    }

    private Set<String> getCompliantLicenses(String license) {
        Set<String> compatibleLicenses = new HashSet<>();
        compatibleLicenses.add(license);
        NodeIterator iter = this.summary.listObjectsOfProperty(ResourceFactory.createResource(license), ResourceFactory.createProperty("http://schema.theodi.org/odrs#compatibleWith"));
        while (iter.hasNext()) {
            Resource compatibleLicense = (Resource) iter.next();
            compatibleLicenses.add(compatibleLicense.getURI());
        }
        return compatibleLicenses;
    }

    private Set<String> getCompliantLicenses(String license1, String license2) {
        Set<String> compatibleLicenses = new HashSet<>(getCompliantLicenses(license1));
        compatibleLicenses.retainAll(getCompliantLicenses(license2));
        return compatibleLicenses;
    }

    private Set<String> getCompliantLicenses(Set<String> licenses) {
        Set<String> compatibleLicenses = new HashSet<>();
        if (!licenses.isEmpty()){
            Iterator<String> licenseIterator = licenses.iterator();
            compatibleLicenses = new HashSet<>(getCompliantLicenses(licenseIterator.next()));
            while (licenseIterator.hasNext()) {
                compatibleLicenses.retainAll(getCompliantLicenses(licenseIterator.next()));
            }
        }
        return compatibleLicenses;
    }

    public String getLabelLicenses(Set<String> licenses) {
        ArrayList<String> labels = new ArrayList<>();
        for (String licenseURI: licenses) {
            NodeIterator iter = this.summary.listObjectsOfProperty(ResourceFactory.createResource(licenseURI), ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#label"));
            while (iter.hasNext()) {
                labels.add(iter.next().toString());
            }
        }
        return labels.toString();
    }

    public String getLabelLicenses(String license) {
        ArrayList<String> labels = new ArrayList<>();
        NodeIterator iter = this.summary.listObjectsOfProperty(ResourceFactory.createResource(license), ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#label"));
        while (iter.hasNext()) {
            labels.add(iter.next().toString());
        }
        return labels.toString();
    }

    /**
     * Returns the list of sources that are protected with the license that have the most conflicts with other licenses.
     * If conflicts are equals, we remove the least reusable
     * */
    public ArrayList<ArrayList> getSourcesToRemove() {
        ArrayList<ArrayList> listSourcesToRemove = new ArrayList<>();
        int conflicts = 0;
        ArrayList<String> licensesToRemove = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : this.licenseConflicts.entrySet()) {
            String license = entry.getKey();
            if (entry.getValue() > conflicts) {
                conflicts = entry.getValue();
                licensesToRemove.clear();
                licensesToRemove.add(license);
            } else if (entry.getValue() == conflicts) {
                // If conflicts are equal, we take the least reusable
                licensesToRemove.add(license);
            }
        }
        for (String license : licensesToRemove) {
            ArrayList<String> SourcesToRemove = new ArrayList<>();
            for (Map.Entry<String, String> entry : this.sourceLicenses.entrySet()) {
                String sourceLicense = entry.getValue();
                if (license.equals(sourceLicense)) {
                    SourcesToRemove.add(entry.getKey());
                }
            }
            listSourcesToRemove.add(SourcesToRemove);
        }
        log.info(listSourcesToRemove.toString() + " will be removed from sources because of " + conflicts + " license conflicts");
        return listSourcesToRemove;
    }
}
