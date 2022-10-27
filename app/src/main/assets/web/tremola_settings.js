// tremola_settings.js

"use strict";

/**
 * This function returns the default set of settings.
 * @returns {{show_shortnames: boolean, enable_preview: boolean, background_map: boolean, hide_forgotten_conv: boolean,
 * wifi_autoconnect: boolean, pub_autoconnect: boolean, hide_forgotten_contacts: boolean}} Default settings dictionary
 */
function get_default_settings() {
    return {
        'enable_preview': false,
        'background_map': true,
        'pub_autoconnect': true,
        'wifi_autoconnect': true,
        'show_shortnames': true,
        'hide_forgotten_conv': true,
        'hide_forgotten_contacts': true
    }
}

/**
 * Called by a switch when it is toggled to modify the tremola settings and serialize it.
 * @param e The switch (typically called with toggle_changed(this)).
 */
function toggle_changed(e) {
    // console.log("toggle ", e.id);
    tremola.settings[e.id] = e.checked;
    persist()
    applySetting(e.id, e.checked);
}

/**
 * Looks up a settings' switch state with its name and returns it.
 * @param nm {String} Name of the setting
 * @returns {Boolean} Whether the setting is on or off
 */
function getSetting(nm) {
    return document.getElementById(nm).checked
}

/**
 * Applies a changed setting once a switch is flipped and follows up on what that changes in the system.
 * Does not change the UI switches or the logical state of the switches that are persisted.
 * @param nm {String} Name of the setting
 * @param val {Boolean} What its updated value should be
 */
function applySetting(nm, val) {
    if (nm === 'background_map') { // Turn background on or off
        if (val)
            document.body.style.backgroundImage = "url('img/splash-as-background.jpg')";
        else
            document.body.style.backgroundImage = null;
    } else if (nm === 'hide_forgotten_conv') { // Reload the chats, toggling visibility of the forgotten conversations
        load_chat_list();
    } else if (nm === 'hide_forgotten_contacts') {// Reload the contacts, toggling visibility of the forgotten ones
        load_contact_list();
    }
}

/**
 * Takes the switch state of val and puts it in the switch defined by nm, also applies it to UI.
 * @param nm {String} Name of the setting
 * @param val {Boolean} What its updated value should be
 */
function setSetting(nm, val) {
    // console.log("setting", nm, val)
    applySetting(nm, val);
    document.getElementById(nm).checked = val;
}

/**
 * Called by the panic button in the settings menu. Once it is called, deletes all data from the device and resets the
 * app.
 * TODO overwrite data multiple times with random values instead of just setting it to null to make it resistant to
 *  forensics
 * @returns {Promise<void>}
 */
async function settings_wipe() {
    closeOverlay();
    backend("wipe");
    window.localStorage.setItem("tremola", "null");
    backend("ready"); // will call initialize()
    await new Promise(resolve => setTimeout(resolve, 500));
    // resetTremola();
    menu_redraw();
    setScenario('chats');
}

// eof
