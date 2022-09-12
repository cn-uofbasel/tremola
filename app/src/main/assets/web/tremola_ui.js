// tremola_ui.js

"use strict";

// True when an overlay is on, false if there is none
let overlayIsActive = false;

// List of all gui elements which can be toggled on or off
const display_or_not = [
    'div:qr', 'div:back',
    'core', 'lst:chats', 'lst:posts', 'lst:contacts', 'lst:members', 'the:connex',
    'div:footer', 'div:textarea', 'div:confirm-members', 'plus',
    'div:settings'
];

// Store the current scenario and the one to return to, should the back button be pressed
let prev_scenario = 'chats';
let curr_scenario = 'chats';

// Array of the scenarios that have a button in the footer
const main_scenarios = ['chats', 'contacts', 'connex'];

// The list of buttons which btnBridge() uses to assert that it should switch scenarios
const buttonList = ['btn:chats', 'btn:posts', 'btn:contacts', 'btn:connex'];

/**
 * The html elements contained by each scenario.
 * It is assumed that each scenario containing 'div:footer' has a corresponding button in
 * tremola.html#div:footer <br>
 * The scenarios are as follows:
 * <ul>
 * <li> chats: shows all the chats </li>
 * <li> contacts: shows the list of contacts </li>
 * <li> posts: detail view of a chat, showing the conversation </li>
 * <li> connex: shows the list of pubs </li>
 * <li> members: when creating a new chat, shows the list of available contacts </li>
 * <li> settings: the settings menu </li>
 * </ul>
 */
const scenarioDisplay = {
    'chats': ['div:qr', 'core', 'lst:chats', 'div:footer', 'plus'],
    'contacts': ['div:qr', 'core', 'lst:contacts', 'div:footer', 'plus'],
    'posts': ['div:back', 'core', 'lst:posts', 'div:textarea'],
    'connex': ['div:qr', 'core', 'the:connex', 'div:footer', 'plus'],
    'members': ['div:back', 'core', 'lst:members', 'div:confirm-members'],
    'settings': ['div:back', 'div:settings']
}

// Contains the options of the top right menu for each scenario as pairs of names and functions to call
const scenarioMenu = {
    'chats': [['New conversation', 'menu_new_conversation'],
        ['Settings', 'menu_settings'],
        ['About', 'menu_about']],
    'contacts': [['New contact', 'menu_new_contact'],
        ['Settings', 'menu_settings'],
        ['About', 'menu_about']],
    'connex': [['New SSB pub', 'menu_new_pub'],
        ['Redeem invite code', 'menu_invite'],
        ['Settings', 'menu_settings'],
        ['About', 'menu_about']],
    'posts': [['Rename', 'menu_edit_convname'],
        ['(un)Forget', 'menu_forget_conv'],
        ['Settings', 'menu_settings'],
        ['About', 'menu_about']],
    'members': [['Settings', 'menu_settings'],
        ['About', 'menu_about']],

    'settings': [] // FIXME visible, but will not open upon click
}

/**
 * This is called when a back button is pressed (typically in the posts scenario)
 * If there is an overlay, it is closed
 * If we are in the chats scenario, we call backend("onBackPressed"), which closes the app
 * If we are in another main scenario, we return to chat
 * If we are not in a main scenario, we return to the previous scenario
 */
function onBackPressed() {
    if (overlayIsActive) {
        closeOverlay();
        return;
    }
    if (main_scenarios.indexOf(curr_scenario) >= 0) {
        if (curr_scenario === 'chats')
            backend("onBackPressed");
        else
            setScenario('chats')
    } else {

        // Special case where the settings scenario displays special elements, so they have to be set right
        // (Might not be necessary, but we don't want to touch code we don't have to)
        if (curr_scenario === 'settings') {
            document.getElementById('div:settings').style.display = 'none';
            document.getElementById('core').style.display = null;
            document.getElementById('div:footer').style.display = null;
        }
        setScenario(prev_scenario);
    }
}

/**
 * This function changes to a new scenario
 * @param new_scenario {String} A string which is the name of the scenario that we want to switch to
 */
function setScenario(new_scenario) {
    // console.log('setScenario ' + new_scenario)

    // This contains the html elements we want to have displayed for the new scenario
    const list_of_elements_to_display = scenarioDisplay[new_scenario];
    if (list_of_elements_to_display) {
        // if (new_scenario != 'posts' && curr_scenario != "members" && curr_scenario != 'posts') {

        // Remove highlight from the footer buttons if applicable
        if (scenarioDisplay[curr_scenario].indexOf('div:footer') >= 0) {
            const cl = document.getElementById('btn:' + curr_scenario).classList;
            cl.toggle('active', false);
            cl.toggle('passive', true);
        }

        // Cycle through the list of elements and check against  list_of_elements_to_display to display it or not
        display_or_not.forEach(function (gui_element) {
            // console.log(' l+' + gui_element);
            if (list_of_elements_to_display.indexOf(gui_element) < 0) { // Not in list: turn off
                document.getElementById(gui_element).style.display = 'none';
            } else { // In list: turn on
                document.getElementById(gui_element).style.display = null;
                // console.log(' l=' + gui_element);
            }
        })

        // Display the red TREMOLA title or another one
        if (new_scenario === "posts" || new_scenario === "settings") { // No title in this scenario, turn off
            document.getElementById('tremolaTitle').style.display = 'none';
            document.getElementById('conversationTitle').style.display = null;
        } else { // Turn on title
            document.getElementById('tremolaTitle').style.display = null;
            document.getElementById('conversationTitle').style.display = 'none';
        }
        if (main_scenarios.indexOf(new_scenario) >= 0) {
            prev_scenario = new_scenario;
        }

        // Turn on highlight on current button in footer if applicable
        curr_scenario = new_scenario;
        if (scenarioDisplay[curr_scenario].indexOf('div:footer') >= 0) {
            const cl = document.getElementById('btn:' + curr_scenario).classList;
            cl.toggle('active', true);
            cl.toggle('passive', false);
        }

    } else { // We received a new_scenario string which is not one of the elements of scenarioDisplay
        launch_snackbar("Error: Illegal new_scenario name in setScenario()")
    }
}

/**
 * A function which changes the scenario or pops up the menu, depending on which button calls it
 * @param element The button that was pressed (usually called as btnBridge(this)
 */
function btnBridge(element) {
    element = element.id;
    let menu = '';
    if (buttonList.indexOf(element) >= 0) { // The pressed button corresponds to a scenario, so we switch to it
        setScenario(element.substring(4));
        return;
    }
    if (element === 'btn:menu') { // The top right button was pressed, open overflow menu
        if (scenarioMenu[curr_scenario].length === 0) // Current scenario has no options for the overflow menu
            return;

        // Make the transparent overlay and menu visible
        document.getElementById("menu").style.display = 'initial';
        document.getElementById("overlay-trans").style.display = 'initial';

        // Cycle through options for menu in this location and add them as list of buttons
        scenarioMenu[curr_scenario].forEach(function (e) {
            menu += "<button class=menu_item_button ";
            menu += "onclick='" + e[1] + "();'>" + e[0] + "</button><br>";
            // console.log(`Scenario menu: ${menu}`);
        })
        menu = menu.substring(0, menu.length - 4);
        document.getElementById("menu").innerHTML = menu;
        return;
    }

    // The element did not correspond to any known case, display error message
    launch_snackbar("Error: Element of btnBridge() is not recognized")
    // if (typeof Android != "undefined") { Android.onFrontendRequest(element); }
}

/**
 * Opens the settings scenario, called from the menu
 */
function menu_settings() {
    closeOverlay();
    setScenario('settings')
    /*
    prev_scenario = curr_scenario;
    curr_scenario = 'settings';
    document.getElementById('core').style.display = 'none';
    document.getElementById('div:footer').style.display = 'none';
    document.getElementById('div:settings').style.display = null;

    document.getElementById("tremolaTitle").style.display = 'none';
    */

    // Display Settings title
    const c = document.getElementById("conversationTitle");
    c.style.display = null;
    c.innerHTML = "<div style='text-align: center;'><font size=+1><strong>Settings</strong></font></div>";
}

/**
 * If an overlay or menu is active, this closes it. If no overlay or menu is active, nothing happens.
 */
function closeOverlay() {
    document.getElementById('menu').style.display = 'none';
    document.getElementById('qr-overlay').style.display = 'none';
    document.getElementById('preview-overlay').style.display = 'none';
    document.getElementById('new_chat-overlay').style.display = 'none';
    document.getElementById('new_contact-overlay').style.display = 'none';
    document.getElementById('confirm_contact-overlay').style.display = 'none';
    document.getElementById('overlay-bg').style.display = 'none';
    document.getElementById('overlay-trans').style.display = 'none';
    document.getElementById('about-overlay').style.display = 'none';
    document.getElementById('edit-overlay').style.display = 'none';
    document.getElementById('old_contact-overlay').style.display = 'none';
    overlayIsActive = false;
}

/**
 * This is used when a message is sent. If the option "Preview before sending" is on, this will open a window to allow
 * the user to double-check the message before sending it. If it is off, it will directly send it.
 */
function showPreview() {
    const draft = escapeHTML(document.getElementById('draft').value);
    if (draft.length === 0) return; // No message entered, do nothing
    if (!getSetting("enable_preview")) { // Setting for preview is off, send it directly
        new_post(draft);
        return;
    }
    // Original message, but newlines are escaped as html <br> tags
    const draft2 = draft.replace(/\n/g, "<br>\n");

    // Recipients of the message, human-readable
    const to = recps2display(tremola.chats[curr_chat].members)

    // Display the draft
    document.getElementById('preview').innerHTML = "To: " + to + "<hr>" + draft2 + "&nbsp;<hr>";
    const s = document.getElementById('preview-overlay').style;
    s.display = 'initial';
    s.height = '80%'; // 0.8 * docHeight;
    document.getElementById('overlay-bg').style.display = 'initial';
    overlayIsActive = true;
}

/**
 * This opens the about overlay, typically invoked from the top right menu
 */
function menu_about() {
    closeOverlay()
    document.getElementById('about-overlay').style.display = 'initial';
    document.getElementById('overlay-bg').style.display = 'initial';
    overlayIsActive = true;
}

/**
 * Invoked when the plus button is pressed, it has a different function based on the scenario it was called in.
 * Chats: Opens a new chat by switching to members scenario
 * Contacts: Opens the new_contact-overlay to add a contact
 * Connex: Opens the edit-overlay to add a pub
 */
function plus_button() {
    closeOverlay();
    if (curr_scenario === 'chats') {
        menu_new_conversation();
    } else if (curr_scenario === 'contacts') {
        menu_new_contact();
    } else if (curr_scenario === 'connex') {
        menu_new_pub();
    }
}

/**
 * Shows a snackbar (small infobox) at the bottom of the screen with a given text, which fades after 3 seconds
 * @param txt {String} The text to be displayed
 */
function launch_snackbar(txt) {
    const sb = document.getElementById("snackbar");
    sb.innerHTML = txt;
    sb.className = "show";
    setTimeout(function () {
        sb.className = sb.className.replace("show", "");
    }, 3000);
}

// --- QR display and scan

/**
 * Generates a QR code containing one's own identity
 */
function showQR() {
    generateQR('did:ssb:ed25519:' + myId.substring(1).split('.')[0])
}

/**
 * Takes a string, generates a corresponding QR code and displays it on screen in the qr-overlay, together with the
 * original text
 * @param s {String} Contains the text to be encoded
 */
function generateQR(s) {
    document.getElementById('qr-overlay').style.display = 'initial';
    document.getElementById('overlay-bg').style.display = 'initial';
    document.getElementById('qr-text').innerHTML = s;
    if (!qr) { // No QR generated so far, create new object and fill it
        let w = window.getComputedStyle(document.getElementById('qr-overlay')).width;
        w = parseInt(w, 10);
        const e = document.getElementById('qr-code');
        const arg = {
            height: w,
            width: w,
            text: s,
            correctLevel: QRCode.CorrectLevel.M // L, M, Q, H
        };
        qr = new QRCode(e, arg);
    } else { // QR has already been generated, overwrite object
        qr.clear();
        qr.makeCode(s);
    }
    overlayIsActive = true;
}

/**
 * Frontend method that calls backend to start QR scan
 */
function qr_scan_start() {
    // TODO test if Android is defined ...
    backend("qrscan.init");
    closeOverlay();
}

/**
 * Called after a successful scan, uses the text it found to make a new contact and the user can add an alias
 * @param s {String} The text that was scanned
 */
function qr_scan_success(s) {
    closeOverlay();
    const t = "did:ssb:ed25519:";
    if (s.substring(0, t.length) === t) { // Modify the string so it has the right format
        s = '@' + s.substring(t.length) + '.ed25519';
    }
    // TODO what if string is invalid?
    let b = '';
    try {
        // Decode data from QR
        b = atob(s.substr(1, s.length - 9));
        // TODO replace the substr() method with substring
        // TODO we should also test whether it is a valid ed25519 public key ...
    } catch (err) {
        launch_snackbar("Error: Could not decode data given in qr_scan_success.");
    }
    if (b.length !== 32) {
        launch_snackbar("Error: Unknown format or invalid identity in qr_scan_success");
        return;
    }
    new_contact_id = s;
    // console.log("tremola:", tremola)
    if (new_contact_id in tremola.contacts) {
        launch_snackbar("This contact already exists");
        return;
    }
    // FIXME: do sanity tests
    // Opens overlay to let you add an alias to the new contact
    menu_edit('new_contact_alias', "Assign alias to new contact:<br>(only you can see this alias)", "");
}

/**
 * Called when a scan failed, displays error message on snackbar.
 */
function qr_scan_failure() {
    launch_snackbar("QR scan failed")
}

/**
 * Called when a contact is added and an alias has been entered.
 * It adds the contact to the backend and saves it in the browser storage.
 */
function qr_scan_confirmed() {
    // The alias given to the contact by the user
    const a = document.getElementById('alias_text').value;
    // The actual ID of the contact
    const s = document.getElementById('alias_id').innerHTML;
    // c = {alias: a, id: s};
    // The initial of the contact
    const i = (a + "?").substring(0, 1).toUpperCase()
    // Generates string for backend: the entered alias, the initial and a random color.
    const c = {"alias": a, "initial": i, "color": colors[Math.floor(colors.length * Math.random())]};
    tremola.contacts[s] = c;
    persist(); // Saves to browser storage
    backend("add:contact " + s + " " + btoa(a))
    load_contact_item([s, c]);
    closeOverlay();
}

/**
 * This function uses the lookup algorithm to find the SSB ID belonging to a shortname.
 Check that entered shortname follows the correct pattern.
 Upper cases are accepted, and the minus in 6th position is optional.
 We use z-base32: char '0', 'l', 'v' and '2' are replaced with
 'o', '1', 'u' and 'z' for less confusion.
 * @param shortname {String} The shortname to search the SSB ID to.
 */
function look_up(shortname) {
    const shortnameLength = 10; // Cannot be coded into the regEx
    // console.log(`shortname: ${shortname}`)
    shortname = shortname.toLowerCase()
        .replace(/0/g, "o")
        .replace(/l/g, "1")
        .replace(/v/g, "u")
        .replace(/2/g, "z");

    if (shortname.search("^[a-z1-9]{5}[a-z1-9]{5}$") !== -1) // No - in string, so we add it back in
        shortname = shortname.slice(0, shortnameLength / 2) + '-' + shortname.slice(shortnameLength / 2, shortnameLength)
    if (shortname.search("^[a-z1-9]{5}-[a-z1-9]{5}$") !== -1) {
        closeOverlay()
        backend("look_up " + shortname);
    } else {
        launch_snackbar(`"${shortname}" is not a valid shortname`)
    }
}

// ---