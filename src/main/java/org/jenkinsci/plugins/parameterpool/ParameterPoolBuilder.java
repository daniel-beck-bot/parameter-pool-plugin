package org.jenkinsci.plugins.parameterpool;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Builder for parameter pool values.
 */
public class ParameterPoolBuilder extends Builder {

    private final String projects;

    private final String name;

    private final String values;

    private final boolean preferError;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ParameterPoolBuilder(String projects, String name, String values, boolean preferError) {
        this.projects = projects;
        this.name = name;
        this.values = values;
        this.preferError = preferError;
    }

    public String getProjects() {
        return projects;
    }

    public String getName() {
        return name;
    }

    public String getValues() {
        return values;
    }

    public boolean isPreferError() {
        return preferError;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException,
            InterruptedException {

        List<AbstractProject> projectsToUse = new ArrayList<AbstractProject>();
        if (StringUtils.isBlank(projects)) {
            projectsToUse.add(build.getProject());
        } else {
            for (String potentialName : projects.split(",")) {
                AbstractProject matchingProject = AbstractProject.findNearest(potentialName);
                if (matchingProject == null) {
                    throw new IllegalArgumentException("Project name " + potentialName + " was not found!");
                }
                projectsToUse.add(matchingProject);
            }
        }


        List<Run> builds = new ArrayList<Run>();
        for (AbstractProject project : projectsToUse) {
            builds.addAll(project.getBuilds());
        }

        Collections.sort(builds, new Comparator<Run>() {
            @Override
            public int compare(Run firstRun, Run secondRun) {
                return new Long(secondRun.getStartTimeInMillis()).compareTo(firstRun.getStartTimeInMillis());
            }
        });

        PrintStream logger = listener.getLogger();
        EnvVars env = build.getEnvironment(listener);

        String expandedName = env.expand(name);
        String expandedValues = env.expand(values);

        ParameterParser parameterParser = new ParameterParser(expandedValues);

        if (parameterParser.getValues().isEmpty()) {
            throw new IllegalArgumentException("No values set for name " + expandedName);
        }

        logger.println("Parsed following values from input text " + expandedValues);
        logger.println(parameterParser.valuesAsText());


        PoolValueSelector valueSelector = new PoolValueSelector();

        String selectedPoolValue = valueSelector.selectPoolValue(expandedName, preferError,
                build.getNumber(), builds, logger, parameterParser.getValues());

        logger.println("Adding " + expandedName + " as environment variable with value of " + selectedPoolValue);

        ParameterEnvAction envAction = new ParameterEnvAction();
        envAction.add(expandedName, selectedPoolValue);

        build.addAction(envAction);

        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }


    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a parameter name");
            if (value.length() < 2)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public FormValidation doCheckValues(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set parameter values");
            return FormValidation.ok();
        }

        /**
         * Checks that the project names entered are valid.
         * Blank means that the current project name is used.
         */
        public FormValidation doCheckProjects(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value ) {
            // Require CONFIGURE permission on this project
            if(!project.hasPermission(Item.CONFIGURE)){
                return FormValidation.ok();
            }
            StringTokenizer tokens = new StringTokenizer(Util.fixNull(value),",");
            while(tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (StringUtils.isBlank(projectName)) {
                    continue;
                }
                Item item = Jenkins.getInstance().getItem(projectName,project,Item.class); // only works after version 1.410
                if(item==null){
                    return FormValidation.error("Project name " + projectName + " not found, did you mean "
                            + AbstractProject.findNearest(projectName).getName());
                }
                if(!(item instanceof AbstractProject)){
                    return FormValidation.error("Project " + projectName + " is not buildable");
                }
            }
            return FormValidation.ok();
        }

        /**
         * Autocompletes project names
         */
        public AutoCompletionCandidates doAutoCompleteProjects(@QueryParameter String value, @AncestorInPath ItemGroup context) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<Job> jobs = Jenkins.getInstance().getAllItems(Job.class);
            for (Job job: jobs) {
                String relativeName = job.getRelativeNameFrom(context);
                if (relativeName.startsWith(value)) {
                    if (job.hasPermission(Item.READ)) {
                        candidates.add(relativeName);
                    }
                }
            }
            return candidates;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Parameter Pool";
        }

    }

}

