package org.jenkinsci.plugins.parameterpool;

import hudson.model.Result;

import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Wrapper for collections of pool values from running, successful and failed builds.
 */
public class BuildPoolValues {

    Set<String> valuesFromRunningBuilds = new LinkedHashSet<String>();
    Set<String> valuesFromFailedBuilds = new LinkedHashSet<String>();
    Set<String> valuesFromFunctionalBuilds = new LinkedHashSet<String>();
    Set<String> allValues = new LinkedHashSet<String>();

    public void addPoolValue(Result buildResult, String poolValue) {
        allValues.add(poolValue);
        if (buildResult == Result.NOT_BUILT) {
            valuesFromRunningBuilds.add(poolValue);
            valuesFromFunctionalBuilds.remove(poolValue);
            valuesFromFailedBuilds.remove(poolValue);
        } else if (buildResult == Result.SUCCESS || buildResult == Result.UNSTABLE) {
            if (!valuesFromFailedBuilds.contains(poolValue) && !valuesFromRunningBuilds.contains(poolValue)) {
                valuesFromFunctionalBuilds.add(poolValue);
            }
        } else {
            if (!valuesFromFunctionalBuilds.contains(poolValue) && !valuesFromRunningBuilds.contains(poolValue)) {
                valuesFromFailedBuilds.add(poolValue);
            }
        }
    }

    public String selectValue(Set<String> allowedValues, boolean preferError) {
        String value;
        if (preferError) {
            value = selectValueIn(allowedValues, valuesFromFailedBuilds);
            if (value != null) {
                return value;
            }
        }

        value = selectValueNotIn(allowedValues, allValues);

        if (value != null) {
            return value;
        }

        value = selectValueIn(allowedValues, valuesFromFunctionalBuilds);

        if (value != null) {
            return value;
        }

        value = selectValueIn(allowedValues, valuesFromFailedBuilds);

        return value;
    }

    private String selectValueNotIn(Set<String> allowedValues, Set<String> values) {
        for (String allowedValue : allowedValues) {
            if (!values.contains(allowedValue)) {
                return allowedValue;
            }
        }
        return null;
    }


    private String selectValueIn(Set<String> allowedValues, Set<String> values) {
        for (String allowedValue : allowedValues) {
            if (values.contains(allowedValue)) {
                return allowedValue;
            }
        }
        return null;
    }


    public void printValues(PrintStream logger) {
        printResult("running", valuesFromRunningBuilds, logger);
        printResult("functional", valuesFromFunctionalBuilds, logger);
        printResult("non functional", valuesFromFailedBuilds, logger);
    }

    private void printResult(String buildDescription, Set<String> values, PrintStream logger) {
        if (values.isEmpty()) {
            logger.println("No values parsed from " + buildDescription + " builds");
        } else {
            logger.println("Parsed following pool values from " + buildDescription + " builds " + values.toString());
        }
    }
}
