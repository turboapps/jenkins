package org.jenkinsci.plugins.spoontrigger.validation;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Optional;
import hudson.model.Item;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.spoontrigger.utils.Credentials;

public final class CredentialValidator implements Validator<String> {

    private final Item project;

    public CredentialValidator(Item project) {
        this.project = project;
    }

    @Override
    public void validate(String credentialsId) throws ValidationException {
        Optional<StandardUsernamePasswordCredentials> credentials = Credentials.lookupById(StandardUsernamePasswordCredentials.class, project, credentialsId);

        if (credentials.isPresent()) {
            return;
        }

        String errMsg = String.format("Cannot find any credentials with id (%s)", credentialsId);
        FormValidation formValidation = FormValidation.warning(errMsg);
        throw new ValidationException(formValidation);
    }
}
