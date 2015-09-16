package org.jenkinsci.plugins.parameterpool;

import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Used to select appropriate pool value.
 */
public class PoolValueSelector {

    public String selectPoolValue(String parameterName, boolean preferError,
                                  String buildDisplayName, List<Run> builds, PrintStream logger,
                                   Set<String> allowedValues) {
        BuildPoolValues poolValues = new BuildPoolValues();
        int completedBuildsChecked = 0;
        for (Run build : builds) {
            if (build.getFullDisplayName().equals(buildDisplayName)) {
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

            ParameterEnvAction parameterEnvAction = build.getAction(ParameterEnvAction.class);
            ParametersAction parametersAction = build.getAction(ParametersAction.class);

            String parameterValue = null;
            String parameterInfo = "No value found";
            if (parameterEnvAction != null) {
                parameterValue = parameterEnvAction.getValue(parameterName);
                parameterInfo = parameterValue + " (from parameter pool)";
            } else if (parametersAction != null) {
                ParameterValue matchingParameter = parametersAction.getParameter(parameterName);
                if (matchingParameter != null) {
                    parameterValue = String.valueOf(matchingParameter.getValue());
                    parameterInfo = parameterValue + " (as build parameter)";
                }
            }

            logger.println(String.format("%s, result %s, %s: %s",
                    build.getFullDisplayName(), result.toString(), parameterName, parameterInfo));

            if (parameterValue == null) {
                if (parameterEnvAction != null) {
                    logger.println("No pool value named " + parameterName + " added in build " + buildDisplayName);
                    logger.println("Pool parameters in build: " + parameterEnvAction.getNames().toString());
                } else if (parametersAction != null) {
                    logger.println("No parameter named " + parameterName + " added in build " + buildDisplayName);
                    logger.println("Parameters in build: " + createParameterNameList(parametersAction).toString());
                } else {
                    logger.println("No parameter pool or parameters found for build " + build.getFullDisplayName());
                }
                continue;
            }

            poolValues.addPoolValue(result, parameterValue);
        }
        poolValues.printValues(logger);

        String value = poolValues.selectValue(allowedValues, preferError);
        if (value == null) {
            throw new IllegalArgumentException("No allowable value found! All of these values were taken: "
                    + allowedValues.toString());
        }
        return value;
    }

    private List<String> createParameterNameList(ParametersAction parameterValues) {
        List<String> names = new ArrayList<String>();
        for (ParameterValue parameterValue : parameterValues.getParameters()) {
            names.add(parameterValue.getName());
        }
        return names;
    }

}
