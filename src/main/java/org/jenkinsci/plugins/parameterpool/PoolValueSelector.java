package org.jenkinsci.plugins.parameterpool;

import hudson.model.Result;
import hudson.model.Run;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;

/**
 * Used to select appropriate pool value.
 */
public class PoolValueSelector {

    public String selectPoolValue(String parameterName, boolean preferError,
                                  int currentBuildNumber, List<Run> builds, PrintStream logger,
                                   Set<String> allowedValues) {
        BuildPoolValues poolValues = new BuildPoolValues();
        int completedBuildsChecked = 0;
        for (Run build : builds) {
            int buildNumber = build.getNumber();
            if (buildNumber == currentBuildNumber) {
                continue;
            }

            // there could be running builds further before completed builds
            if (completedBuildsChecked > 20) {
                break;
            }
            Result result = build.isBuilding() ? Result.NOT_BUILT : build.getResult();
            if (result != Result.NOT_BUILT) {
                completedBuildsChecked ++;
            }

            String poolValue = null;
            ParameterEnvAction parameterEnvAction = build.getAction(ParameterEnvAction.class);
            if (parameterEnvAction != null) {
                poolValue = parameterEnvAction.getValue(parameterName);
            }

            logger.println(String.format("Build number %s, result %s, %s: %s",
                    buildNumber, result.toString(), parameterName, poolValue));
            if (parameterEnvAction == null) {
                logger.println("No " + ParameterEnvAction.class.getSimpleName() + " found for build " + buildNumber);
                continue;
            }

            if (poolValue == null) {
                logger.println("No value named " + parameterName + " added in build " + buildNumber);
                logger.println("Pool parameters in build: " + parameterEnvAction.getNames().toString());
                continue;
            }
            poolValues.addPoolValue(result, poolValue);
        }
        poolValues.printValues(logger);

        String value = poolValues.selectValue(allowedValues, preferError);
        if (value == null) {
            throw new IllegalArgumentException("No allowable value found! All of these values were taken: "
                    + allowedValues.toString());
        }
        return value;
    }

}
