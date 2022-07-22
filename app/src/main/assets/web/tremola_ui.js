// tremola_ui.js

"use strict";

let overlayIsActive = false;

let display_or_not = [
    'div:qr', 'div:back',
    'core', 'lst:chats', 'lst:posts', 'lst:contacts', 'lst:members', 'the:connex',
    'div:footer', 'div:textarea', 'div:confirm-members', 'plus', 'btn:createCoor', 'readOnlyCoord',
    'newCoord', 'closedCoord','newCoordTitle', 'openCoordTitle', 'closedCoordTitle', 'btn:voteCoor', 'btn:completeCoor',
    'div:settings', 'lst:meetings', 'btn:forgetCoor'
];

let prev_scenario = 'chats';
let curr_scenario = 'chats';

// Array of the scenarios that have a button in the footer
const main_scenarios = ['chats', 'contacts', 'connex', 'newMeetings', 'openMeetings', 'closedMeetings', 'readOnlyCoord'];

const buttonList = ['btn:chats', 'btn:posts', 'btn:contacts', 'btn:connex', 'btn:createCoor', 'btn:voteCoor', 'btn:completeCoor', 'btn:forgetCoor'];

/**
 * The elements contained by each scenario.
 * It is assumed that each scenario containing 'div:footer' has a
 * corresponding button in tremola.html#div:footer
 */
let scenarioDisplay = {
    'chats': ['div:qr', 'core', 'lst:chats', 'div:footer', 'plus'],
    'contacts': ['div:qr', 'core', 'lst:contacts', 'div:footer', 'plus'],
    'posts': ['div:back', 'core', 'lst:posts', 'div:textarea'],
    'connex': ['div:qr', 'core', 'the:connex', 'div:footer', 'plus'],
    'members': ['div:back', 'core', 'lst:members', 'div:confirm-members'],
    'settings': ['div:back', 'div:settings'],
    'newMeetings': ['div:back', 'core', 'btn:createCoor','newCoordTitle', 'newCoord'],
    'openMeetings': ['div:back', 'core', 'openCoordTitle', 'readOnlyCoord', 'btn:voteCoor', 'btn:completeCoor', 'btn:forgetCoor'],
    'closedMeetings': ['div:back', 'core', 'closedCoordTitle', 'btn:test', 'closedCoord' , 'lst:meetings'],

}
/**
* Scenario Menu
* All elements which are contained inside can be shown on the menu (top right corner)
* If you want to implement new ones use newMeetings as an example.
* For it to work you need a scenario in scenarioDisplay
*/
let scenarioMenu = {
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
        ['New Meeting', 'menu_new_meeting'],
        ['Show Meetings', 'menu_closed_meetings'],
        ['Settings', 'menu_settings'],
        ['About', 'menu_about']],
    'members': [['Settings', 'menu_settings'],
        ['About', 'menu_about']],
    'meetings': ['newMeetings', 'openMeetings', ['Show Meetings', 'menu_closed_meetings'],
        ['Settings', 'menu_settings'],['About', 'menu_about']],
    'newMeetings': [['Show Meetings', 'menu_closed_meetings'],
                   ['Settings', 'menu_settings'],['About', 'menu_about']],
    'openMeetings': [['Show Meetings', 'menu_closed_meetings'],
                    ['Settings', 'menu_settings'],['About', 'menu_about']],
    'closedMeetings': [['New Meeting', 'menu_new_meeting'], ['(un)Forget Meetings', 'menu_toggle_forget'],
                    ['Toggle only Active Meetings', 'menu_toggle_active'], ['Settings', 'menu_settings'],['About', 'menu_about']],
    'settings': []
}

/**
* Function which decides what to show if you press on the backbutton of your phone or on the app
* mostly used to remove html elements from the front page
*/
function onBackPressed() {
    if (overlayIsActive) {
        closeOverlay();
        return;
    }
    if (main_scenarios.indexOf(curr_scenario) >= 0) {
        if (curr_scenario === 'chats') {
            backend("onBackPressed");
        } else if (curr_scenario === 'openMeetings') {
            menu_closed_meetings();
        } else
            setScenario('chats')
    } else {
        if (curr_scenario === 'settings') {
            document.getElementById('div:settings').style.display = 'none';
            document.getElementById('core').style.display = null;
            document.getElementById('div:footer').style.display = null;
        }
        setScenario(prev_scenario);
        if(prev_scenario === 'closedMeetings' && curr_scenario === 'closedMeetings'){
            document.getElementById('tremolaTitle').style.display = 'none';
            }
        if(prev_scenario === 'newMeetings' && curr_scenario === 'newMeetings'){
            document.getElementById('tremolaTitle').style.display = 'none';
            }
    }
}

function setScenario(new_scenario) {
    // console.log('setScenario ' + new_scenario)
    let list_of_elements = scenarioDisplay[new_scenario];
    if (list_of_elements) {
        // if (new_scenario != 'posts' && curr_scenario != "members" && curr_scenario != 'posts') {

        // Activate and deactivate the buttons in the footer
        if (scenarioDisplay[curr_scenario].indexOf('div:footer') >= 0) {
            let cl = document.getElementById('btn:' + curr_scenario).classList;
            cl.toggle('active', false);
            cl.toggle('passive', true);
        }
        // Cycle throw the list of elements and check against the list in
        // scenarioDisplay to display it or not
        display_or_not.forEach(function (gui_element) {
            // console.log(' l+' + gui_element);
            if (list_of_elements.indexOf(gui_element) < 0) {
                document.getElementById(gui_element).style.display = 'none';
            } else {
                document.getElementById(gui_element).style.display = null;
                // console.log(' l=' + gui_element);
            }
        })
        // Display the red TREMOLA title or another one
        if (new_scenario === "posts" || new_scenario === "settings" || new_scenario  === "meetings") {
            document.getElementById('tremolaTitle').style.display = 'none';
            document.getElementById('conversationTitle').style.display = null;
        } else {
            document.getElementById('tremolaTitle').style.display = null;
            document.getElementById('conversationTitle').style.display = 'none';
        }
        if (main_scenarios.indexOf(new_scenario) >= 0) {
            prev_scenario = new_scenario;
        }
        curr_scenario = new_scenario;
        if (scenarioDisplay[curr_scenario].indexOf('div:footer') >= 0) {
            var cl = document.getElementById('btn:' + curr_scenario).classList;
            cl.toggle('active', true);
            cl.toggle('passive', false);
        }
    }
}

function btnBridge(element) {
    element = element.id;
    let menu = '';
    if (buttonList.indexOf(element) >= 0) {
        setScenario(element.substring(4));
    }
    if (element === 'btn:menu') {
        if (scenarioMenu[curr_scenario].length === 0)
            return;
        document.getElementById("menu").style.display = 'initial';
        document.getElementById("overlay-trans").style.display = 'initial';
        scenarioMenu[curr_scenario].forEach(function (e) {
            menu += "<button class=menu_item_button ";
            menu += "onclick='" + e[1] + "();'>" + e[0] + "</button><br>";
            console.log(`Scenario menu: ${menu}`);
        })
        menu = menu.substring(0, menu.length - 4);
        document.getElementById("menu").innerHTML = menu;

    }
    // if (typeof Android != "undefined") { Android.onFrontendRequest(element); }
}

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
    var c = document.getElementById("conversationTitle");
    c.style.display = null;
    c.innerHTML = "<div style='text-align: center;'><font size=+1><strong>Settings</strong></font></div>";
}

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

function showPreview() {
    var draft = escapeHTML(document.getElementById('draft').value);
    if (draft.length === 0) return;
    if (!getSetting("enable_preview")) {
        new_post(draft);
        return;
    }
    var draft2 = draft.replace(/\n/g, "<br>\n");
    var to = recps2display(tremola.chats[curr_chat].members)
    document.getElementById('preview').innerHTML = "To: " + to + "<hr>" + draft2 + "&nbsp;<hr>";
    var s = document.getElementById('preview-overlay').style;
    s.display = 'initial';
    s.height = '80%'; // 0.8 * docHeight;
    document.getElementById('overlay-bg').style.display = 'initial';
    overlayIsActive = true;
}

function menu_about() {
    closeOverlay()
    document.getElementById('about-overlay').style.display = 'initial';
    document.getElementById('overlay-bg').style.display = 'initial';
    overlayIsActive = true;
}

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

function launch_snackbar(txt) {
    var sb = document.getElementById("snackbar");
    sb.innerHTML = txt;
    sb.className = "show";
    setTimeout(function () {
        sb.className = sb.className.replace("show", "");
    }, 3000);
}

// --- QR display and scan

function showQR() {
    generateQR('did:ssb:ed25519:' + myId.substring(1).split('.')[0])
}

function generateQR(s) {
    document.getElementById('qr-overlay').style.display = 'initial';
    document.getElementById('overlay-bg').style.display = 'initial';
    document.getElementById('qr-text').innerHTML = s;
    if (!qr) {
        var w, e, arg;
        w = window.getComputedStyle(document.getElementById('qr-overlay')).width;
        w = parseInt(w, 10);
        e = document.getElementById('qr-code');
        arg = {
            height: w,
            width: w,
            text: s,
            correctLevel: QRCode.CorrectLevel.M // L, M, Q, H
        };
        qr = new QRCode(e, arg);
    } else {
        qr.clear();
        qr.makeCode(s);
    }
    overlayIsActive = true;
}

function qr_scan_start() {
    // test if Android is defined ...
    backend("qrscan.init");
    closeOverlay();
}

function qr_scan_success(s) {
    closeOverlay();
    var t = "did:ssb:ed25519:";
    if (s.substring(0, t.length) === t) {
        s = '@' + s.substring(t.length) + '.ed25519';
    }
    var b = '';
    try {
        b = atob(s.substr(1, s.length - 9));
        // FIXME we should also test whether it is a valid ed25519 public key ...
    } catch (err) {
    }
    if (b.length !== 32) {
        launch_snackbar("unknown format or invalid identity");
        return;
    }
    new_contact_id = s;
    // console.log("tremola:", tremola)
    if (new_contact_id in tremola.contacts) {
        launch_snackbar("This contact already exists");
        return;
    }
    // FIXME: do sanity tests
    menu_edit('new_contact_alias', "Assign alias to new contact:<br>(only you can see this alias)", "");
}

function qr_scan_failure() {
    launch_snackbar("QR scan failed")
}

function qr_scan_confirmed() {
    var a = document.getElementById('alias_text').value;
    var s = document.getElementById('alias_id').innerHTML;
    // c = {alias: a, id: s};
    var i = (a + "?").substring(0, 1).toUpperCase()
    var c = {"alias": a, "initial": i, "color": colors[Math.floor(colors.length * Math.random())]};
    tremola.contacts[s] = c;
    persist();
    backend("add:contact " + s + " " + btoa(a))
    load_contact_item([s, c]);
    closeOverlay();
}

/**
 * Check that entered ShortName follows the correct pattern.
 * Upper cases are accepted, and the minus in 6th position is optional.
 * We use z-base32: char '0', 'l', 'v' and '2' are replaced with
 * 'o', '1', 'u' and 'z' for less confusion.
 */
function look_up(shortname) {
    const shortnameLength = 10; // Cannot be coded into the regEx
    console.log(`shortname: ${shortname}`)
    shortname = shortname.toLowerCase()
        .replace(/0/g, "o")
        .replace(/l/g, "1")
        .replace(/v/g, "u")
        .replace(/2/g, "z");

    if (shortname.search("^[a-z1-9]{5}[a-z1-9]{5}$") !== -1)
        shortname = shortname.slice(0, shortnameLength / 2) + '-' + shortname.slice(shortnameLength / 2, shortnameLength)
    if (shortname.search("^[a-z1-9]{5}-[a-z1-9]{5}$") !== -1) {
        closeOverlay()
        backend("look_up " + shortname);
    } else {
        launch_snackbar(`"${shortname}" is not a valid Shortname`)
    }
}

// --- Meeting coordination

/**
* This is the menu option toggle forget found in the scenario "closedMeetings"
* It uses the global var isForgotten (tremola.js) which is on true and hides the buttons
* and the entry from the user. With another click it makes them all visible.
*/
function menu_toggle_forget(){
    closeOverlay();
    //the global var isForgotten is initialized as true in tremola.js
    if(!isForgotten){
        launch_snackbar("Forgotten Meetings are now hidden");
        isForgotten = true;
        menu_closed_meetings();
        container.replaceChildren();
        //console.log("Toggle first time" + isForgotten);
    }
    if(isForgotten){
        launch_snackbar("Forgotten Meetings are now visible");
        isForgotten = false;
        menu_closed_meetings();
        //console.log("Toggle second time" + isForgotten);
    }
}

/**
* Function to hide all the completed entrys in the button list from scenario closed_meetings.
* Works the same way as menu_toggle_forgotten only difference is,
* var isCompleted is initialized as false
*/
function menu_toggle_active(){

    closeOverlay();
    if(!isCompleted){
        document.getElementById('closedCoordTitle').innerHTML = "Only Active Meetings";
        document.getElementById('closedCoordTitle').style.color = '#e85132';
        launch_snackbar("Only active Meetings are visible");
        isCompleted = true;
        menu_closed_meetings();
        container.replaceChildren();
        //console.log("Toggle first comp " + isCompleted);
    } else{
        isCompleted = false;
        menu_closed_meetings();
        document.getElementById('closedCoordTitle').innerHTML = "All Meetings";
        launch_snackbar("All Meetings are now visible");
        //console.log("Toggle second comp " + isCompleted);
    }
}

/**
* Menu option to switch to scenario new_meeting
*/
function menu_new_meeting() {
    //console.log("Members MyID" + myId );
    //console.log("Members All" + recps);
    //console.log("Members First" + recps.substring(0,53));
    //console.log("Members First Letter" + recps[1]);
    var recps = tremola.chats[curr_chat].members.join(' ');
    setScenario('newMeetings');
    document.getElementById("tremolaTitle").style.display = 'none';
    closeOverlay();
}

/**
* Menu option to switch to scenario open_meetings
* Is not used on the GUI
*/
function menu_open_meetings() {
    setScenario('openMeetings');
    loadPoll(); // loads the meeting data of the group conversations unique poll
    loadVotes(); // loads counter votes
    document.getElementById("tremolaTitle").style.display = 'none';
    //document.getElementById("btn:menu").style.display = 'none';
    closeOverlay();
}

/**
* Menu option to switch to the scenario closed_meetings
*/
function menu_closed_meetings() {
    setScenario('closedMeetings');
    document.getElementById("tremolaTitle").style.display = 'none';
    closeOverlay();
    load_meeting_list();
}

/**
* An old function which lets deactivated the vote and create button, if the user wasn't the
* group host. Isn't used but can be used for different purposes
*/
function clearCoor() {
    var recps = tremola.chats[curr_chat].members.join(' ');
    console.log("Members All" + recps);
    console.log("Members First" + recps.substring(0,53));
    console.log("Members First Letter" + recps[1]);
    if(myId != recps.substring(0,53)){
        setScenario('openMeetings');
        launch_snackbar("You have not the authority to close a meeting");
        document.getElementById("btn:clearCoor").disabled = true;
    } else{
        var clear = document.getElementById("btn:clearCoor");
        var voteBut = document.getElementById("btn:voteCoor");
        var createBut = document.getElementById("btn:createCoor");
        document.getElementById("btn:voteCoor").disabled = false;
        document.getElementById("btn:createCoor").disabled = false;
    }
}

/**
* Function for the button btn:completeCoor in scenario opened_meetings
* only can be used if the user is also the maker of the pool
* sends a message to the backend wiith the tag of "2:"
*/
function completeCoor() {
    var recps = tremola.chats[curr_chat].members.join(' ');
    var me = recps2nm([myId]);
    //check if the user is also the creator of the poll
    if (tremola.polls[curr_chat][curr_poll].creator == me) {
        // close meeting
        var msg = btoa("2:" + curr_chat + "%" + curr_poll);
        backend("meet:vote" + " " + msg + " " + recps);
        launch_snackbar("Meeting closed!");
    } else {
        launch_snackbar("You are not the creator of this meeting.");
    }
}

/**
* Function for the button btn:forgetCoor in scenario opened_meetings
* to forget or unforget the meeting
*/
function forgetBtn() {
    if (tremola.polls[curr_chat][curr_poll].forgotten == true) {
        tremola.polls[curr_chat][curr_poll].forgotten = false;
        launch_snackbar("Meeting unforgotten!");
    } else if (tremola.polls[curr_chat][curr_poll].forgotten == false) {
        tremola.polls[curr_chat][curr_poll].forgotten = true;
        launch_snackbar("Meeting forgotten!");
    }
}
/**
* Function for the button btn:voteCoor which
*/
function voteCoor() {
    // prevent additional votes locally
    var me = recps2nm([myId]);
    for (var m in tremola.polls[curr_chat][curr_poll].membervotes) {
        if (m == me) {
            var votes = tremola.polls[curr_chat][curr_poll].membervotes[me];
            if (votes[0] != 999) {
                launch_snackbar("You have voted already!");
                return;
            }
        }
    }
    // variables for the title and the counters of votes
    var rot = document.getElementById("roTitle").value;
    var vote1;
    var vote2;
    var vote3;
    var vote4;
    var vote5;
    var vote6;
    var vote7;
    var vote8;
    var vote9;
    var vote10;
    // variables for the checkmarks
    var checkB1 = document.getElementById("voteButton1");
    var checkB2 = document.getElementById("voteButton2");
    var checkB3 = document.getElementById("voteButton3");
    var checkB4 = document.getElementById("voteButton4");
    var checkB5 = document.getElementById("voteButton5");
    var checkB6 = document.getElementById("voteButton6");
    var checkB7 = document.getElementById("voteButton7");
    var checkB8 = document.getElementById("voteButton8");
    var checkB9 = document.getElementById("voteButton9");
    var checkB10 = document.getElementById("voteButton10");

    if(checkB1.checked == true) {vote1 = 1;} else {vote1 = 0;};
    if(checkB2.checked == true) {vote2 = 1;} else{vote2 = 0;};
    if(checkB3.checked == true) {vote3 = 1;} else{vote3 = 0;};
    if(checkB4.checked == true) {vote4 = 1;} else{vote4 = 0;};
    if(checkB5.checked == true) {vote5 = 1;} else{vote5 = 0;};
    if(checkB6.checked == true) {vote6 = 1;} else{vote6 = 0;};
    if(checkB7.checked == true) {vote7 = 1;} else{vote7 = 0;};
    if(checkB8.checked == true) {vote8 = 1;} else{vote8 = 0;};
    if(checkB9.checked == true) {vote9 = 1;} else{vote9 = 0;};
    if(checkB10.checked == true) {vote10 = 1;} else{vote10 = 0;};
    //send message to backend
    var recps = tremola.chats[curr_chat].members.join(' ');
    var me = recps2nm([myId]);
    var votes = vote1 + "," + vote2 + "," + vote3 + "," + vote4 + "," + vote5 +
        "," + vote6 + "," + vote7 + "," + vote8 + "," + vote9 + "," + vote10;
    launch_snackbar("Voted!");
    // 1: -> single vote
    var msg = btoa("1:" + curr_poll + "%" + me + "%" + Date.now() + "%" + votes);
    backend("meet:vote" + " " + msg + " " + recps);
    //hide button from scenario 2 after clicking it
    var btn = document.getElementById("btn:voteCoor");
    // disable checkmarks after vote
    for (var i = 1; i < 11; i++) {
        document.getElementById("voteButton" + i).disabled = true;
    }
}
/**
* Function for the button btn:createCoor in the scenario newMeeting;
* Creates a message from all the fields in the scenario and sends it to the backend:
* complete string to backend: "meet:dates btoa(rot%me%meetID%Date.now()%msg) recps"
* msg: date1,startT1,endT1%...%date10,startT10,endT10
*/
function create_new_meeting() {
    if (document.getElementById("title").value == "") {
        launch_snackbar("Title missing!");
        return;
    }
    var cnt = 0;
    for (var i = 1; i < 11; i++) {
        if (document.getElementById("date" + i).value == "") {
            cnt++;
        }
    }
    if (cnt == 10) {
        launch_snackbar("Add a date!");
        return;
    }
    var rot = document.getElementById("title").value;
    document.getElementById("menu").style.display = 'initial';
    document.getElementById("overlay-trans").style.display = 'initial';
    var date = new Array(10);
    var startT = new Array(10);
    var endT = new Array(10);
    var msgString = "";
    for (var i = 1; i < 11; i++) {
        date[i] = document.getElementById("date" + i).value;
        startT[i] = document.getElementById("from" + i).value;
        endT[i] = document.getElementById("to" + i).value;

        if(date[i] == "") {date[i] = "NULL"};
        if(startT[i] == "") {startT[i] = "NULL"};
        if(endT[i] == "") {endT[i] = "NULL"};

        msgString += "%" + date[i] + "," + startT[i] + "," + endT[i];
    }
    var recps = tremola.chats[curr_chat].members.join(' ');
    var meetID = Math.floor(100000000 * Math.random());
    curr_poll = meetID;
    var me = recps2nm([myId]);

    if(curr_scenario === 'newMeetings'){
        // msgString = %date1,startT1,endT1%...%date10,startT10,endT10
        backend("meet:dates " + btoa(rot + "%" + me + "%"+ meetID + "%" + Date.now() + msgString) + " " + recps);
        document.getElementById("title").value = "";
        for (var i = 1; i < 11; i++) {
            document.getElementById("date" + i).value = "";
            document.getElementById("from" + i).value = "";
            document.getElementById("to" + i).value = "";
        }
        menu_open_meetings();
        setScenario('openMeetings');
        document.getElementById("tremolaTitle").style.display = 'none';
    }
    curr_scenario = 'openMeetings';


    for (var i = 1; i < 11; i++) {
        document.getElementById("voteButton" + i).checked = false;
        document.getElementById("voteButton" + i).disabled = false;
    }
    console.log("setCheck: create: ", document.getElementById("voteButton1").disabled);
}

/*
* Fills out all dates and times in the voting poll scene.
*/
function loadPoll() {
    setVisible(); // resets date and time fields
    if (curr_chat in tremola.polls && curr_poll in tremola.polls[curr_chat]) {
        var arrMeetings = tremola.polls[curr_chat][curr_poll].dates;
        document.getElementById("roTitle").value = arrMeetings[0][0];
        for (var i = 1; i < arrMeetings.length; i++) {
            if (arrMeetings[i][0] !== "NULL") {
                document.getElementById("roDate" + i).value = arrMeetings[i][0];
            } else {
                document.getElementById("roDate" + i).value = "";
                document.getElementById("roDate" + i).style.display = 'none';
                document.getElementById("roFrom" + i).style.display = 'none';
                document.getElementById("voteButton" + i).style.display = 'none';
                document.getElementById("voteCounter" + i).style.display = 'none';
            }
            if (arrMeetings[i][1] !== "NULL" && arrMeetings[i][2] !== "NULL") {
                document.getElementById("roFrom" + i).value = arrMeetings[i][1] + " - " + arrMeetings[i][2];
            } else if (arrMeetings[i][1] !== "NULL") {
                document.getElementById("roFrom" + i).value = arrMeetings[i][1];
            } else if (arrMeetings[i][2] !== "NULL") {
                document.getElementById("roFrom" + i).value = "            - " + arrMeetings[i][2];
            } else {
                document.getElementById("roFrom" + i).value = "";
            }
        }
    }
}

/*
* Loads the counters in the voting form scene to show the total distribution of votes.
*/
function loadVotes() {
    if (curr_poll in tremola.polls[curr_chat]) {
        var votes = tremola.polls[curr_chat][curr_poll].votes;
        for (var i = 0; i < votes.length; i++) {
            var text = "✓: " + votes[i];
            document.getElementById("voteCounter" + (i+1)).innerHTML = "✓: " + votes[i];
        }
    }
}

// --- Deprecated functions

/**
* @Deprecated
*
* Fills out all dates in the scene open meetings for receivers
* (members of group where meeting was made)
*/
function fillOutMeetingDates(arrMeetings) {
    document.getElementById("roTitle").value = arrMeetings[0][0];

    for (var i = 1; i < arrMeetings.length; i++) {
        if (arrMeetings[i][0] !== "NULL") {
            document.getElementById("roDate" + i).value = arrMeetings[i][0];
        }

        if (arrMeetings[i][1] !== "NULL" && arrMeetings[i][2] !== "NULL") {
            document.getElementById("roFrom" + i).value = arrMeetings[i][1] + " - " + arrMeetings[i][2];
        } else if (arrMeetings[i][1] !== "NULL") {
            document.getElementById("roFrom" + i).value = arrMeetings[i][1];
        } else if (arrMeetings[i][2] !== "NULL") {
            document.getElementById("roFrom" + i).value = "            - " + arrMeetings[i][2];
        } else {
            document.getElementById("roFrom" + i).value = "";
        }
    }

}