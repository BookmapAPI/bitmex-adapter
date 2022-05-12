package com.bookmap.plugins.layer0.bitmex;

import java.awt.Container;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import com.bookmap.plugins.layer0.bitmex.adapter.Constants;

import velox.api.layer0.credentialscomponents.CredentialsCheckbox;
import velox.api.layer0.credentialscomponents.CredentialsComponent;
import velox.api.layer0.credentialscomponents.CredentialsComponentUtil;
import velox.api.layer0.credentialscomponents.CredentialsFieldManager;
import velox.api.layer0.credentialscomponents.CredentialsSerializationField;
import velox.api.layer0.credentialscomponents.CredentialsTextField;

public class BitmexFieldManager implements CredentialsFieldManager {

    public static class CustomCheckbox extends CredentialsCheckbox {

        public boolean previousTradingCheckboxValue;

        public CustomCheckbox(String name, boolean isKey, String label) {
            super(name, isKey, label);
        }
    }

    CredentialsTextField apiKeyField = new CredentialsTextField(Constants.API_KEY_FIELD_NAME, true,
            Constants.API_KEY_FIELD_LABEL, false);
    CredentialsTextField apiSecretField = new CredentialsTextField(Constants.API_SECRET_FIELD_NAME, true,
            Constants.API_SECRET_FIELD_LABEL, true);
    CustomCheckbox tradingCheckbox = new CustomCheckbox(Constants.ENABLE_TRADING_CHECKBOX_NAME, true,
            Constants.ENABLE_TRADING_CHECKBOX_LABEL);
    CustomCheckbox isDemoCheckbox = new CustomCheckbox(Constants.IS_DEMO_CHECKBOX_NAME, true,
            Constants.IS_DEMO_CHECKBOX_LABEL);

    List<CredentialsComponent> components = new LinkedList<>();

    {
        apiKeyField.addAlias(CredentialsComponentUtil.LEGACY_USERNAME_ALIAS, Constants.API_KEY_FIELD_NAME);
        apiSecretField.addAlias(CredentialsComponentUtil.LEGACY_PASSWORD_ALIAS, Constants.API_SECRET_FIELD_NAME);
        isDemoCheckbox.addAlias(CredentialsComponentUtil.LEGACY_DEMO_ALIAS, Constants.IS_DEMO_CHECKBOX_NAME);
        components.add(isDemoCheckbox);
        components.add(tradingCheckbox);
        components.add(apiKeyField);
        components.add(apiSecretField);

        tradingCheckbox.addSettingsChangedListener(
            () -> {
                repaintApiFields();
            }
        );
    }

    @Override
    public List<CredentialsComponent> getCredentialsComponents() {
        repaintApiFields();
        return components;
    }

    private void repaint(boolean isSelected, CredentialsComponent credentialsComponent) {
        Container container = credentialsComponent.getCompoundComponent();
        container.setVisible(isSelected);
    }

    @Override
    public boolean isConfigured(Map<String, CredentialsSerializationField> arg0) {
        return (
            isNoneNull(apiKeyField, apiSecretField) &&
            (!isTradingEnabled() || areApiKeyFieldsFilled())
        );
    }

    private boolean isNoneNull(Object... objects) {
        for (Object object : objects) {
            if (object == null) return false;
        }
        return true;
    }

    private boolean isTradingEnabled() {
        return tradingCheckbox.getValue();
    }

    private boolean areApiKeyFieldsFilled() {
        return StringUtils.isNoneBlank(
            apiKeyField.getValue(),
            apiSecretField.getValue()
        );
    }

    private void repaint(boolean isSelected, CredentialsComponent... credentialsComponents) {
        for (CredentialsComponent credentialsComponent : credentialsComponents) {
            repaint(isSelected, credentialsComponent);
        }
    }

    private void repaintApiFields() {
        repaint(tradingCheckbox.getValue(), apiKeyField, apiSecretField);
    }

}
