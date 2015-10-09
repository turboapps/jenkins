package org.jenkinsci.plugins.spoontrigger;

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public class SpoonProject extends Project<SpoonProject, SpoonBuild> implements TopLevelItem {

    @Extension
    @Restricted({NoExternalUse.class})
    public static final SpoonProject.DescriptorImpl DESCRIPTOR = new SpoonProject.DescriptorImpl();

    public SpoonProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    protected Class<SpoonBuild> getBuildClass() {
        return SpoonBuild.class;
    }

    @Override
    public SpoonProject.DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final class DescriptorImpl extends AbstractProjectDescriptor {

        public String getDisplayName() {
            return "Turbo project";
        }

        public SpoonProject newInstance(ItemGroup parent, String name) {
            return new SpoonProject(parent, name);
        }
    }
}
