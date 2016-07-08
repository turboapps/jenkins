package org.jenkinsci.plugins.spoontrigger.utils;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Optional;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.spoontrigger.validation.*;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

public final class Credentials {

    private static final DomainRequirement ANY_DOMAIN = new DomainRequirement();
    private static final Validator<String> CREDENTIALS_ID_VALIDATOR = StringValidators.isNotNull("Credentials are required to login to a Turbo account", Level.WARNING);

    public static <T extends UsernameCredentials> Optional<T> lookupById(Class<T> aClass, String credentialId) {
        List<T> availableCredentials = lookupByItemGroup(aClass, Jenkins.getInstance());
        return Optional.fromNullable(CredentialsMatchers.firstOrNull(availableCredentials, CredentialsMatchers.withId(credentialId)));
    }

    public static <T extends UsernameCredentials> Optional<T> lookupById(Class<T> aClass, Item item, String credentialId) {
        List<T> availableCredentials = lookupByItem(aClass, item);
        return Optional.fromNullable(CredentialsMatchers.firstOrNull(availableCredentials, CredentialsMatchers.withId(credentialId)));
    }

    public static <T extends UsernameCredentials> List<T> lookupByItem(Class<T> aClass, Item item) {
        return CredentialsProvider.lookupCredentials(aClass, item, ACL.SYSTEM, ANY_DOMAIN);
    }

    private static <T extends UsernameCredentials> List<T> lookupByItemGroup(Class<T> aClass, ItemGroup itemGroup) {
        return CredentialsProvider.lookupCredentials(aClass, itemGroup, ACL.SYSTEM, ANY_DOMAIN);
    }

    public static ListBoxModel fillCredentialsIdItems(@AncestorInPath Item project) {
        if (doNotHasPermissions(project)) {
            return new StandardListBoxModel();
        }

        List<StandardUsernamePasswordCredentials> projectCredentials = Credentials.lookupByItem(StandardUsernamePasswordCredentials.class, project);
        return new StandardListBoxModel().withEmptySelection().withAll(projectCredentials);
    }

    public static ListBoxModel fillCredentialsIdItems() {
        List<StandardUsernamePasswordCredentials> projectCredentials = Credentials.lookupByItem(StandardUsernamePasswordCredentials.class, null);
        return new StandardListBoxModel().withEmptySelection().withAll(projectCredentials);
    }

    private static boolean doNotHasPermissions(Item project) {
        return project == null || !project.hasPermission(Item.CONFIGURE);
    }
}
