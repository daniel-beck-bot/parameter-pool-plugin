package org.jenkinsci.plugins.parameterpool;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents an action for adding an environmental variable for a parameter.
 */
public class ParameterEnvAction implements EnvironmentContributingAction {

    private Map<String,String> data = new HashMap<String,String>();

    public void add(String key, String val) {
        if (data==null) return;
        data.put(key, val);
    }

    public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
        if (data!=null) env.putAll(data);
    }

    public String getIconFileName() { return null; }
    public String getDisplayName() { return null; }
    public String getUrlName() { return null; }

    public String getValue(String name) {
        return data != null ? data.get(name) : null;
    }

    public Set<String> getNames() {
        return data != null ? data.keySet() : new HashSet<String>();
    }
}
