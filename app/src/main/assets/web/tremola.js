// tremola.js

"use strict";

// The primary object that gets serialized and stored. Contains contacts dictionary, among other things
let tremola;
// Variable to keep track which chat we are in.
let curr_chat;
// This object contains the QR code that was last generated.
let qr;
// Contains the user's SSB ID.
let myId;
// List of peers in a local network.
let localPeers = {}; // feedID ~ [isOnline, isConnected] - TF, TT, FT - FF means to remove this entry
// Variable whether the UI has to be redrawn or not.
let must_redraw = false;
// This variable keeps track what the edit_overlay was used for.
let edit_target = '';
// The ID of the new contact being generated.
let new_contact_id = '';
// An array of nice colors.
const colors = ["#d9ceb2", "#99b2b7", "#e6cba5", "#ede3b4", "#8b9e9b", "#bd7578", "#edc951",
    "#ffd573", "#c2a34f", "#fbb829", "#ffab03", "#7ab317", "#a0c55f", "#8ca315",
    "#5191c1", "#6493a7", "#bddb88"]
// The list of known pubs.
let pubs = []

// --- menu callbacks

/*
function menu_sync() {
  if (localPeers.length == 0)
    launch_snackbar("no local peer to sync with");
  else {
    for (var i in localPeers) {
      backend("sync " + i);
      launch_snackbar("sync launched");
      break
    }
  }
  closeOverlay();
}
*/

/**
 * Sets up the members scenario after the plus button was pressed in the chats scenario.
 */
function menu_new_conversation() {
    fill_members()
    prev_scenario = 'chats'
    setScenario("members")
    document.getElementById("div:textarea").style.display = 'none';
    document.getElementById("div:confirm-members").style.display = 'flex';
    document.getElementById("tremolaTitle").style.display = 'none';
    const c = document.getElementById("conversationTitle");
    c.style.display = null;
    c.innerHTML = "<font size=+1><strong>Create New Conversation</strong></font><br>Select up to 7 members";
    document.getElementById('plus').style.display = 'none';
}

/**
 * Sets up the new_contact-overlay after the plus button was pressed in the contacts scenario.
 */
function menu_new_contact() {
    document.getElementById('new_contact-overlay').style.display = 'initial';
    document.getElementById('overlay-bg').style.display = 'initial';
    // document.getElementById('chat_name').focus();
    overlayIsActive = true;
}

/**
 * Sets up the edit-overlay for entering the address of a pub after the plus button was pressed in the connex scenario.
 */
function menu_new_pub() {
    menu_edit('new_pub_target', "Enter address of trustworthy pub<br><br>Format:<br><tt>net:IP_ADDR:PORT~shs:ID_OF_PUB</tt>", "");
}

/**
 * Sets up the edit-overlay for redeeming an invite code after "Redeem invite code" was selected from the top-right menu
 * in the connex scenario.
 */
function menu_invite() {
    menu_edit('new_invite_target', "Enter invite code<br><br>Format:<br><tt>IP_ADDR:PORT:@ID_OF_PUB.ed25519~INVITE_CODE</tt>", "");
}

/**
 * Resends all messages when the user selects "Re-stream posts" in the settings scenario.
 */
function menu_stream_all_posts() {
    // closeOverlay();
    setScenario('chats')
    launch_snackbar("DB restreaming launched");
    backend("restream");
}

/**
 * Refreshes the UI on command. It reloads the current conversation, the list of chats and the contacts.
 */
function menu_redraw() {
    closeOverlay();

    load_chat_list()

    document.getElementById("lst:contacts").innerHTML = '';
    load_contact_list();

    if (curr_scenario === "posts")
        load_chat(curr_chat);
}

/**
 * Resets the UI but keeps the current ID.
 * Returns to chats scenario, wipes all chats, contacts, pubs, etc. but keeps the ID.
 * Does NOT delete the contact data in the backend, which is used for lookups for example.
 */
function menu_reset() {
    closeOverlay();
    resetTremola();
    setScenario('chats');
    menu_redraw();
    launch_snackbar("reloading DB");
    backend("reset");
}

/**
 * Opens the edit-overlay, in which the title is displayed along with a text input field.
 * @param target {String} Overwrites the edit_target, so other functions know what the input was for.
 * @param title {String} The title to display to the user.
 * @param text {String} The text already in the editable text field.
 */
function menu_edit(target, title, text) {
    closeOverlay()
    document.getElementById('edit-overlay').style.display = 'initial';
    document.getElementById('overlay-bg').style.display = 'initial';
    document.getElementById('edit_title').innerHTML = title;
    document.getElementById('edit_text').value = text;
    document.getElementById('edit_text').focus();
    overlayIsActive = true;
    edit_target = target;
}

/**
 * Opens an edit-overlay to change the name of a conversation when selecting the "Rename" option in the top-right menu
 * in the posts scenario.
 */
function menu_edit_convname() {
    menu_edit('convNameTarget',
        "Edit conversation name:<br>(only you can see this name)",
        tremola.chats[curr_chat].alias);
}

// function menu_edit_new_contact_alias() {
//   menu_edit('new_contact_alias', "Assign alias to new contact:", "");
// }

/**
 * Called when the user added a contact. Adds the contact to the tremola object in the frontend and also to the backend.
 * Opens a chat with the newly added user and saves.
 * @param alias The alias of the contact to be added.
 * @param public_key The public key of the contact to be added.
 */
function edit_confirmed_back(alias, public_key) {
    console.log("edit_confirmed_back: " + alias + ", " + public_key)
    tremola.contacts[new_contact_id] = {
        "alias": alias, "initial": alias.substring(0, 1).toUpperCase(),
        "color": colors[Math.floor(colors.length * Math.random())]
    };
    const recps = [myId, new_contact_id];
    const nm = recps2nm(recps);
    tremola.chats[nm] = {
        "alias": "Chat w/ " + alias, "posts": {}, "members": recps,
        "touched": Date.now(), "lastRead": 0
    };
    persist();
    backend("add:contact " + new_contact_id + " " + btoa(alias))
    menu_redraw();
}

/**
 * Called when an edit-overlay is closed by confirming. Depending on what the overlay was for, follows up in different
 * ways.
 * <ul>
 *     <li> Change the name of the conversation. </li>
 *     <li> Add a contact with their alias. </li>
 *     <li> Add a pub (unfinished). </li>
 *     <li> Redeem an invite code. </li>
 * </ul>
 */
function edit_confirmed() {
    closeOverlay()
    const val = document.getElementById('edit_text').value;
    if (edit_target === 'convNameTarget') { // User was editing the name of a conversation
        // Update the name and save it, redraw page
        const ch = tremola.chats[curr_chat];
        ch.alias = val;
        persist();
        load_chat_title(ch); // also have to update entry in chats
        menu_redraw();
    } else if (edit_target === 'new_contact_alias' || edit_target === 'trust_wifi_peer') { // User was adding a contact
        document.getElementById('contact_id').value = '';
        if (val === '')
            id2b32(new_contact_id, 'edit_confirmed_back')
        else
            edit_confirmed_back(val, new_contact_id)
    } else if (edit_target === 'new_pub_target') { // User was adding a pub FIXME does not do anything
        console.log("action for new_pub_target")
    } else if (edit_target === 'new_invite_target') { // User was redeeming an invite code
        backend("invite:redeem " + val)
    }
}

/**
 * Toggles the forgotten attribute of a chat. Being forgotten makes it hidden in the UI but does not delete it.
 * Your own chat will prompt an error. Forgetting a chat will put you in the chats scenario, unforgetting it will reload
 * it.
 */
function menu_forget_conv() {
    if (curr_chat === recps2nm([myId])) { // You cannot forget the chat with yourself.
        launch_snackbar("You cannot forget your own notes.");
        return;
    }
    tremola.chats[curr_chat].forgotten = !tremola.chats[curr_chat].forgotten;
    persist();
    load_chat_list() // refresh list of conversations
    closeOverlay();
    if (curr_scenario === 'posts' /* should always be true */ && tremola.chats[curr_chat].forgotten)
        setScenario('chats');
    else
        load_chat(curr_chat) // refresh currently displayed list of posts
}

/**
 * Imports the secret ID of another device onto the current one.
 * Not functional. Is supposed to import the secret ID of another device.
 * TODO make this work
 */
function menu_import_id() {
    // backend('secret: XXX');
    launch_snackbar("Not functional at the moment.")
    closeOverlay();
}

function menu_process_msgs() {
    backend('process.msg');
    closeOverlay();
}

function menu_add_pub() {
    // ...
    closeOverlay();
}

function menu_dump() {
    backend('dump:');
    closeOverlay();
}

// ---

function new_post(s) {
    if (s.length === 0) {
        return;
    }
    var draft = unicodeStringToTypedArray(document.getElementById('draft').value); // escapeHTML(
    var recps = tremola.chats[curr_chat].members.join(' ')
    backend("priv:post " + btoa(draft) + " " + recps);
    var c = document.getElementById('core');
    c.scrollTop = c.scrollHeight;
    document.getElementById('draft').value = '';
    closeOverlay();
}

function load_post_item(p) { // { 'key', 'from', 'when', 'body', 'to' (if group or public)>
    var pl = document.getElementById('lst:posts');
    var is_other = p["from"] !== myId;
    var box = "<div class=light style='padding: 3pt; border-radius: 4px; box-shadow: 0 0 5px rgba(0,0,0,0.7);'>"
    if (is_other)
        box += "<font size=-1><i>" + fid2display(p["from"]) + "</i></font><br>";
    var txt = escapeHTML(p["body"]).replace(/\n/g, "<br>\n");
    var d = new Date(p["when"]);
    d = d.toDateString() + ' ' + d.toTimeString().substring(0, 5);
    box += txt + "<div align=right style='font-size: x-small;'><i>";
    box += d + "</i></div></div>";
    var row;
    if (is_other) {
        var c = tremola.contacts[p.from]
        row = "<td style='vertical-align: top;'><button class=contact_picture style='margin-right: 0.5em; margin-left: 0.25em; background: " + c.color + "; width: 2em; height: 2em;'>" + c.initial + "</button>"
        // row  = "<td style='vertical-align: top; color: var(--red); font-weight: 900;'>&gt;"
        row += "<td colspan=2 style='padding-bottom: 10px;'>" + box + "<td colspan=2>";
    } else {
        row = "<td colspan=2><td colspan=2 style='padding-bottom: 10px;'>" + box;
        row += "<td style='vertical-align: top; color: var(--red); font-weight: 900;'>&lt;"
    }
    pl.insertRow(pl.rows.length).innerHTML = row;
}

function load_chat(nm) {
    var ch, pl, e;
    ch = tremola.chats[nm]
    pl = document.getElementById("lst:posts");
    while (pl.rows.length) {
        pl.deleteRow(0);
    }
    curr_chat = nm;
    var lop = [];
    for (var p in ch.posts) lop.push(p)
    lop.sort((a, b) => ch.posts[a].when - ch.posts[b].when)
    lop.forEach((p) =>
        load_post_item(ch.posts[p])
    )
    load_chat_title(ch);
    setScenario("posts");
    document.getElementById("tremolaTitle").style.display = 'none';
    // scroll to bottom:
    e = document.getElementById('core')
    e.scrollTop = e.scrollHeight;
    // update unread badge:
    ch["lastRead"] = Date.now();
    persist();
    document.getElementById(nm + '-badge').style.display = 'none' // is this necessary?
}

function load_chat_title(ch) {
    var c = document.getElementById("conversationTitle"), bg, box;
    c.style.display = null;
    c.classList = ch.forgotten ? ['gray'] : []
    box = "<div style='white-space: nowrap;'><div style='text-overflow: ellipsis; overflow: hidden;'><font size=+1><strong>" + escapeHTML(ch.alias) + "</strong></font></div>";
    box += "<div style='color: black; text-overflow: ellipsis; overflow: hidden;'>" + escapeHTML(recps2display(ch.members)) + "</div></div>";
    c.innerHTML = box;
}

function load_chat_list() {
    const meOnly = recps2nm([myId])
    // console.log('meOnly', meOnly)
    document.getElementById('lst:chats').innerHTML = '';
    load_chat_item(meOnly)
    let lop = [];
    for (let p in tremola.chats) {
        if (p !== meOnly && !tremola.chats[p]['forgotten'])
            lop.push(p)
    }
    lop.sort((a, b) => tremola.chats[b]["touched"] - tremola.chats[a]["touched"])
    lop.forEach((p) =>
        load_chat_item(p)
    )
    // forgotten chats: unsorted
    if (!tremola.settings.hide_forgotten_conv)
        for (var p in tremola.chats)
            if (p !== meOnly && tremola.chats[p]['forgotten'])
                load_chat_item(p)
}

function load_chat_item(nm) { // appends a button for conversation with name nm to the conv list
    var cl, mem, item, bg, row, badge, badgeId, cnt;
    cl = document.getElementById('lst:chats');
    mem = recps2display(tremola.chats[nm].members)
    item = document.createElement('div');
    item.style = "padding: 0px 5px 10px 5px; margin: 3px 3px 6px 3px;";
    if (tremola.chats[nm].forgotten) bg = ' gray'; else bg = ' light';
    row = "<button class='chat_item_button w100" + bg + "' onclick='load_chat(\"" + nm + "\");' style='overflow: hidden; position: relative;'>";
    row += "<div style='white-space: nowrap;'><div style='text-overflow: ellipsis; overflow: hidden;'>" + tremola.chats[nm].alias + "</div>";
    row += "<div style='text-overflow: clip; overflow: ellipsis;'><font size=-2>" + escapeHTML(mem) + "</font></div></div>";
    badgeId = nm + "-badge"
    badge = "<div id='" + badgeId + "' style='display: none; position: absolute; right: 0.5em; bottom: 0.9em; text-align: center; border-radius: 1em; height: 2em; width: 2em; background: var(--red); color: white; font-size: small; line-height:2em;'>&gt;9</div>";
    row += badge + "</button>";
    row += ""
    item.innerHTML = row;
    cl.append(item);
    set_chats_badge(nm)
}

function load_contact_list() {
    document.getElementById("lst:contacts").innerHTML = '';
    for (var id in tremola.contacts)
        if (!tremola.contacts[id].forgotten)
            load_contact_item([id, tremola.contacts[id]]);
    if (!tremola.settings.hide_forgotten_contacts)
        for (var id in tremola.contacts) {
            var c = tremola.contacts[id]
            if (c.forgotten)
                load_contact_item([id, c]);
        }
}

function load_contact_item(c) { // [ id, { "alias": "thealias", "initial": "T", "color": "#123456" } ] }
    var row, item = document.createElement('div'), bg;
    item.style = "padding: 0px 5px 10px 5px;";
    if (!("initial" in c[1])) {
        c[1]["initial"] = c[1].alias.substring(0, 1).toUpperCase();
        persist();
    }
    if (!("color" in c[1])) {
        c[1]["color"] = colors[Math.floor(colors.length * Math.random())];
        persist();
    }
    // console.log("load_c_i", JSON.stringify(c[1]))
    bg = c[1].forgotten ? ' gray' : ' light';
    row = "<button class=contact_picture style='margin-right: 0.75em; background: " + c[1].color + ";'>" + c[1].initial + "</button>";
    row += "<button class='chat_item_button" + bg + "' style='overflow: hidden; width: calc(100% - 4em);' onclick='show_contact_details(\"" + c[0] + "\");'>";
    row += "<div style='white-space: nowrap;'><div style='text-overflow: ellipsis; overflow: hidden;'>" + escapeHTML(c[1].alias) + "</div>";
    row += "<div style='text-overflow: clip; overflow: ellipsis;'><font size=-2>" + c[0] + "</font></div></div></button>";
    // var row  = "<td><button class=contact_picture></button><td style='padding: 5px;'><button class='contact_item_button light w100'>";
    // row += escapeHTML(c[1].alias) + "<br><font size=-2>" + c[0] + "</font></button>";
    // console.log(row);
    item.innerHTML = row;
    document.getElementById('lst:contacts').append(item);
}

/**
 * Sets up the UI to display all contacts as a list of options with checkboxes which can be selected.
 * This is vital for the scenario members.
 */
function fill_members() {
    let choices = '';
    for (let m in tremola.contacts) {
        choices += '<div style="margin-bottom: 10px;"><label><input type="checkbox" id="' + m;
        choices += '" style="vertical-align: middle;"><div class="contact_item_button light" style="white-space: nowrap; width: calc(100% - 40px); padding: 5px; vertical-align: middle;">';
        choices += '<div style="text-overflow: ellipis; overflow: hidden;">' + escapeHTML(fid2display(m)) + '</div>';
        choices += '<div style="text-overflow: ellipis; overflow: hidden;"><font size=-2>' + m + '</font></div>';
        choices += '</div></label></div>\n';
    }
    document.getElementById('lst:members').innerHTML = choices
    /*
      <div id='lst:members' style="display: none;margin: 10pt;">
        <div style="margin-top: 10pt;"><label><input type="checkbox" id="toggleSwitches2" style="margin-right: 10pt;"><div class="contact_item_button light" style="display: inline-block;padding: 5pt;">Choice1<br>more text</div></label></div>
      </div>
    */
    document.getElementById(myId).checked = true;
}

function show_contact_details(id) {
    id2b32(id, 'show_contact_details_back')
}

function show_contact_details_back(shortname, id) {
    var c = tremola.contacts[id];
    new_contact_id = id;
    document.getElementById('old_contact_alias').value = c['alias'];
    var details = '';
    details += '<br><div>Shortname: &nbsp;' + shortname + ' </div>\n';
    details += '<br><div style="word-break: break-all;">SSB identity: &nbsp;<tt>' + id + '</tt></div>\n';
    details += '<br><div class=settings style="padding: 0px;"><div class=settingsText>Forget this contact</div><div style="float: right;"><label class="switch"><input id="hide_contact" type="checkbox" onchange="toggle_forget_contact(this);"><span class="slider round"></span></label></div></div>'
    document.getElementById('old_contact_details').innerHTML = details;
    document.getElementById('old_contact-overlay').style.display = 'initial';
    document.getElementById('overlay-bg').style.display = 'initial';
    document.getElementById('hide_contact').checked = c.forgotten;

    document.getElementById('old_contact_alias').focus();
    overlayIsActive = true;
}

function toggle_forget_contact(e) {
    var c = tremola.contacts[new_contact_id];
    c.forgotten = !c.forgotten;
    persist();
    closeOverlay();
    load_contact_list();
}

/**
 * Save a nickname for a user.
 * If not present, save its shortname (computed by the backend) as alias.
 * The backend calls the method {@link save_content_alias_back} directly
 */
function save_content_alias() {
    let alias = document.getElementById('old_contact_alias').value;
    if (alias === '')
        id2b32(new_contact_id, 'save_content_alias_back');
    else
        save_content_alias_back(alias, new_contact_id)
}

function save_content_alias_back(alias, new_contact_id) {
    tremola.contacts[new_contact_id].alias = alias;
    tremola.contacts[new_contact_id].initial = alias.substring(0, 1).toUpperCase();
    tremola.contacts[new_contact_id].color = colors[Math.floor(colors.length * Math.random())];
    persist();
    menu_redraw();
    closeOverlay();
}

function new_conversation() {
    // { "alias":"local notes (for my eyes only)", "posts":{}, "members":[myId], "touched": millis }
    var recps = []
    for (var m in tremola.contacts) {
        if (document.getElementById(m).checked)
            recps.push(m);
    }
    if (recps.indexOf(myId) < 0)
        recps.push(myId);
    if (recps.length > 7) {
        launch_snackbar("Too many recipients");
        return;
    }
    var cid = recps2nm(recps)
    if (cid in tremola.chats) {
        if (tremola.chats[cid].forgotten) {
            tremola.chats[cid].forgotten = false;
            load_chat_list(); // refresh
        } else
            launch_snackbar("Conversation already exists");
        return;
    }
    var nm = recps2nm(recps);
    if (!(nm in tremola.chats)) {
        tremola.chats[nm] = {
            "alias": "Unnamed conversation", "posts": {},
            "members": recps, "touched": Date.now()
        };
        persist();
    } else
        tremola.chats[nm]["touched"] = Date.now()
    load_chat_list();
    setScenario("chats")
    curr_chat = nm
    menu_edit_convname()
}

function load_peer_list() {
    var i, lst = '', row;
    for (i in localPeers) {
        var x = localPeers[i], color, row, nm, tmp;
        if (x[1]) color = ' background: var(--lightGreen);'; else color = '';
        tmp = i.split('~');
        nm = '@' + tmp[1].split(':')[1] + '.ed25519'
        if (nm in tremola.contacts)
            nm = ' / ' + tremola.contacts[nm].alias
        else
            nm = ''
        row = "<button class='flat buttontext' style='border-radius: 25px; width: 50px; height: 50px; margin-right: 0.75em;" + color + "'><img src=img/signal.svg style='width: 50px; height: 50px; margin-left: -3px; margin-top: -3px; padding: 0px;'></button>";
        row += "<button class='chat_item_button light' style='overflow: hidden; width: calc(100% - 4em);' onclick='show_peer_details(\"" + i + "\");'>";
        row += "<div style='white-space: nowrap;'><div style='text-overflow: ellipsis; overflow: hidden;'>" + tmp[0].substring(4) + nm + "</div>";
        row += "<div style='text-overflow: clip; overflow: ellipsis;'><font size=-2>" + tmp[1].substring(4) + "</font></div></div></button>";
        lst += '<div style="padding: 0px 5px 10px 5px;">' + row + '</div>';
        // console.log(row)
    }
    document.getElementById('the:connex').innerHTML = lst;
}

function show_peer_details(id) {
    new_contact_id = "@" + id.split('~')[1].substring(4) + ".ed25519";
    // if (new_contact_id in tremola.constacts)
    //  return;
    menu_edit("trust_wifi_peer", "Trust and Autoconnect<br>&nbsp;<br><strong>" + new_contact_id + "</strong><br>&nbsp;<br>Should this WiFi peer be trusted (and autoconnected to)? Also enter an alias for the peer - only you will see this alias", "?")
}

function getUnreadCnt(nm) {
    var c = tremola.chats[nm], cnt = 0;
    for (var p in c.posts) {
        if (c.posts[p].when > c.lastRead)
            cnt++;
    }
    return cnt;
}

function set_chats_badge(nm) {
    var e = document.getElementById(nm + '-badge'), cnt;
    cnt = getUnreadCnt(nm)
    if (cnt === 0) {
        e.style.display = 'none';
        return
    }
    e.style.display = null;
    if (cnt > 9) cnt = ">9"; else cnt = "" + cnt;
    e.innerHTML = cnt
}

// --- util

function unicodeStringToTypedArray(s) {
    var escstr = encodeURIComponent(s);
    var binstr = escstr.replace(/%([0-9A-F]{2})/g, function (match, p1) {
        return String.fromCharCode('0x' + p1);
    });
    return binstr;
}

/**
 * Takes a string from which it derives a shortname, then calls method_name(<shortname>, str).
 * @param str {String} The string to generate a shortname from, typically an SSB ID.
 * @param method_name {String} The method which is called afterwards with arguments <shortname> and str.
 */
function id2b32(str, method_name) {
    try {
        backend("priv:hash " + str + " " + method_name);
    } catch (err) {
        console.error(err)
    }
}

/**
 * Takes a string and returns string with securely escaped HTML characters.
 * @param str {String} The string to escape.
 * @returns {string} The escaped string.
 */
function escapeHTML(str) {
    return new Option(str).innerHTML;
}

/**
 * Takes a list of SSB IDs, sorts them, concatenates them and removes all .ed25519.
 * Usually used to derive an internal name for a chat or a contact.
 * @param rcps {[String]} An array of SSB IDs.
 * @returns {String} The initial IDs, sorted, concatenated with the .ed25519 removed.
 */
function recps2nm(rcps) {
    return rcps.sort().join('').replace(/.ed25519/g, '')
}

/**
 * Takes a list of recipients and returns them as a human-readable string.
 * TODO write accurate description
 * @param rcps The recipients in the machine-readable form.
 * @returns {string} Human-readable recipients list, enclosed in brackets.
 */
function recps2display(rcps) {
    const lst = rcps.map(fid => {
        return fid2display(fid)
    });
    return '[' + lst.join(', ') + ']';
}

/**
 * Finds a fitting alias for a contact's ID.
 * It takes a friend's ID, looks it up in the tremola.contacts dictionary and retrieves the alias.
 * If nothing was found or the alias is empty, returns the first 9 letters of the contact.
 * @param fid The SSB ID of a friend.
 * @returns {string} The most fitting alias for the ID.
 */
function fid2display(fid) {
    let a = '';
    if (fid in tremola.contacts)
        a = tremola.contacts[fid].alias;
    if (a === '')
        a = fid.substring(0, 9);
    return a;
}

// --- Interface to Kotlin side and local (browser) storage

/**
 * Takes a string to send to the Kotlin backend.
 * If we are not on an Android device, this simulates the most basic functionality of the actual backend for in-browser
 * testing.
 * @param cmdStr The string that gets passed to the backend or is used for the simulated response.
 */
function backend(cmdStr) {
    if (typeof Android != 'undefined') { // Android device: simply call backend
        Android.onFrontendRequest(cmdStr);
        return;
    }

    // Only called on non-Android devices: Simulating the backend functionality for in-browser testing
    cmdStr = cmdStr.split(' ')
    if (cmdStr[0] === 'ready')
        b2f_initialize('@AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=.ed25519')
    else if (cmdStr[0] === 'exportSecret')
        b2f_showSecret('secret_of_id_which_is@AAAA==.ed25519')
    else if (cmdStr[0] === 'priv:post') {
        var draft = atob(cmdStr[1])
        cmdStr.splice(0, 2)
        var e = {
            'header': {
                'tst': Date.now(),
                'ref': Math.floor(1000000 * Math.random()),
                'fid': myId
            },
            'confid': {'type': 'post', 'text': draft, 'recps': cmdStr},
            'public': {}
        }
        // console.log('e=', JSON.stringify(e))
        b2f_new_event(e)
    } else {
        // console.log('backend', JSON.stringify(cmdStr))
    }
}

/**
 * Resets the browser-side content and initializes the tremola object.
 * Initializes the tremola object with one's ID, opens a chat with oneself, adds oneself to contacts and saves.
 */
function resetTremola() { // wipes browser-side content
    tremola = {
        "chats": {},
        "contacts": {},
        "profile": {},
        "id": myId,
        "settings": get_default_settings()
    }
    const n = recps2nm([myId])
    tremola.chats[n] = {
        "alias": "local notes (for my eyes only)", "posts": {}, "forgotten": false,
        "members": [myId], "touched": Date.now(), "lastRead": 0
    };
    tremola.contacts[myId] = {"alias": "me", "initial": "M", "color": "#bd7578", "forgotten": false};
    persist();
}

/**
 * Saves the tremola object to local storage.
 */
function persist() {
    // console.log(tremola);
    window.localStorage.setItem("tremola", JSON.stringify(tremola));
}

function b2f_local_peer(p, status) { // wireless peer: online, offline, connected, disconnected
    console.log("local peer", p, status);
    if (!(p in localPeers))
        localPeers[p] = [false, false]
    if (status === 'online') localPeers[p][0] = true
    if (status === 'offline') localPeers[p][0] = false
    if (status === 'connected') localPeers[p][1] = true
    if (status === 'disconnected') localPeers[p][1] = false
    if (!localPeers[p][0] && !localPeers[p][1])
        delete localPeers[p]
    load_peer_list()
}

/**
 * After a lookup returns a result, a snackbar is launched displaying the result.
 * @param shortname The shortname that we searched for initially.
 * @param public_key The found public key of the person that was looked up.
 */
function snackbar_lookup_back(shortname, public_key) {
    launch_snackbar(shortname  + " : " + public_key)
}

function b2f_new_contact_lookup(target_short_name, new_contact_id) {
    console.log(`new contact lookup ${target_short_name}, ${new_contact_id}`);
    id2b32(new_contact_id, 'snackbar_lookup_back')
    // launch_snackbar(target_short_name, " : ", await id2b32(new_contact_id));

    tremola.contacts[new_contact_id] = {
        "alias": target_short_name,
        "initial": target_short_name.substring(0, 1).toUpperCase(),
        "color": colors[Math.floor(colors.length * Math.random())]
    };
    var recps = [myId, new_contact_id];
    var nm = recps2nm(recps);
    tremola.chats[nm] = {
        "alias": "Chat w/ " + target_short_name, "posts": {}, "members": recps,
        "touched": Date.now(), "lastRead": 0
    };
    persist();
    menu_redraw();
}

function b2f_new_event(e) { // incoming SSB log event: we get map with three entries
    // console.log('hdr', JSON.stringify(e.header))
    // console.log('pub', JSON.stringify(e.public))
    // console.log('cfd', JSON.stringify(e.confid))
    if (e.confid && e.confid.type === 'post') {
        var i, conv_name = recps2nm(e.confid.recps);
        if (!(conv_name in tremola.chats)) { // create new conversation if needed
            tremola.chats[conv_name] = {
                "alias": "Unnamed conversation", "posts": {},
                "members": e.confid.recps, "touched": Date.now(), "lastRead": 0
            };
            load_chat_list()
        }
        for (i in e.confid.recps) {
            var id, r = e.confid.recps[i];
            if (!(r in tremola.contacts))
                id2b32(r, 'b2f_new_event_back')

        }
        var ch = tremola.chats[conv_name];
        if (!(e.header.ref in ch.posts)) { // new post
            // var d = new Date(e.header.tst);
            // d = d.toDateString() + ' ' + d.toTimeString().substring(0,5);
            var p = {"key": e.header.ref, "from": e.header.fid, "body": e.confid.text, "when": e.header.tst};
            ch["posts"][e.header.ref] = p;
            if (ch["touched"] < e.header.tst)
                ch["touched"] = e.header.tst
            if (curr_scenario === "posts" && curr_chat === conv_name) {
                load_chat(conv_name); // reload all messages (not very efficient ...)
                ch["lastRead"] = Date.now();
            }
            set_chats_badge(conv_name)
        }
        // if (curr_scenario == "chats") // the updated conversation could bubble up
        load_chat_list();
        // console.log(JSON.stringify(tremola))
    }
    persist();
    must_redraw = true;
}

function b2f_new_event_back(shortname, publicKey) {
    tremola.contacts[publicKey] = {
        "alias": shortname, "initial": shortname.substring(0, 1).toUpperCase(),
        "color": colors[Math.floor(colors.length * Math.random())]
    }
    load_contact_list()
}

function b2f_new_contact(contact_str) { // '{"alias": "nickname", "id": "fid", 'img' : base64, date}'
    var c = JSON.parse(contact_str)
    load_contact_item(c)
}

function b2f_showSecret(json) {
    setScenario(prev_scenario);
    generateQR(json)
}

/**
 * Sets the app's ID to the provided one, loads the tremola object from browser storage, resets and initializes frontend
 * if tremola object is empty, loads settings, loads chats and contacts and goes to chats scenario.
 * @param id The ID that tremola should use as one's own.
 */
function b2f_initialize(id) {
    myId = id
    if (window.localStorage.tremola) {
        tremola = JSON.parse(window.localStorage.getItem('tremola'));
        if (tremola != null && id !== tremola.id) // check for clash of IDs, erase old state if new
            tremola = null;
    } else
        tremola = null;
    if (tremola == null) {
        resetTremola();
    }
    if (typeof Android == 'undefined')
        console.log("loaded ", JSON.stringify(tremola)) // prints debug information if simulating frontend in browser
    if (!('settings' in tremola))
        tremola.settings = {}
    let nm;
    for (nm in tremola.settings)
        setSetting(nm, tremola.settings[nm])
    load_chat_list()
    load_contact_list()

    closeOverlay();
    setScenario('chats');
}

// --- eof
