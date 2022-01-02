// tremola_ui.js

"use strict";

var overlayIsActive = false;

var display_or_not = [
  'div:qr', 'div:back',
  'core', 'lst:chats', 'lst:posts', 'lst:contacts', 'lst:members', 'the:connex',
  'div:footer', 'div:textarea', 'div:confirm-members', 'plus',
  'div:settings'
];

var prev_scenario = 'chats';
var curr_scenario = 'chats';

var scenarioDisplay = {
  'chats':    ['div:qr', 'core', 'lst:chats', 'div:footer', 'plus'],
  'contacts': ['div:qr', 'core', 'lst:contacts', 'div:footer', 'plus'],
  'posts':    ['div:back', 'core', 'lst:posts', 'div:textarea'],
  'connex':   ['div:qr', 'core', 'the:connex', 'div:footer', 'plus'],
  'members':  ['div:back', 'core', 'lst:members', 'div:confirm-members'],
  'settings': ['div:back', 'div:settings']
}

var scenarioMenu = {
  'chats'    : [['New conversation', 'menu_new_conversation'],
                ['Settings', 'menu_settings'],
                ['About', 'menu_about']],
  'contacts' : [['New contact', 'menu_new_contact'],
                ['Settings', 'menu_settings'],
                ['Lookup', 'menu_look_up'],
                ['About', 'menu_about']],
  'connex'   : [['New SSB pub', 'menu_new_pub'],
                ['Redeem invite code', 'menu_invite'],
                // ['<del>Force sync</del>', 'menu_sync'],
                ['Settings', 'menu_settings'],
                ['About', 'menu_about']],
/*
                ['Redraw', 'menu_redraw'],
                ['Sync', 'menu_sync'],
                ['Redraw', 'menu_redraw'],
                ['Restream', 'menu_stream_all_posts'],
                ['Import ID', 'menu_import_id'],
                ['Process msgs', 'menu_process_msgs'],
                ['Add pub', 'menu_add_pub'],
                ['Dump', 'menu_dump'],
                ['Reset', 'menu_reset']]
*/
  'posts'    : [['Rename', 'menu_edit_convname'],
                ['(un)Forget', 'menu_forget_conv'],
                ['Settings', 'menu_settings'],
                ['About', 'menu_about']],
  'members' :  [['Settings', 'menu_settings'],
                ['About', 'menu_about']],

  'settings' : []
}

function onBackPressed() {
  if (overlayIsActive) {
    closeOverlay();
    return;
  }
  if (['chats', 'contacts', 'connex'].indexOf(curr_scenario) >= 0) {
    if (curr_scenario == 'chats')
      backend("onBackPressed");
    else
      setScenario('chats')
  } else {
    if (curr_scenario == 'settings') {
      document.getElementById('div:settings').style.display = 'none';
      document.getElementById('core').style.display = null;
      document.getElementById('div:footer').style.display = null;
    }
    setScenario(prev_scenario);
  }
}

function setScenario(s) {
  // console.log('setScenario ' + s)
  var lst = scenarioDisplay[s];
  if (lst) {
    // if (s != 'posts' && curr_scenario != "members" && curr_scenario != 'posts') {
    if (['chats', 'contacts', 'connex'].indexOf(curr_scenario) >= 0) {
      var cl = document.getElementById('btn:'+curr_scenario).classList;
      cl.toggle('active', false);
      cl.toggle('passive', true);
    }
    // console.log(' l: ' + lst)
    display_or_not.forEach(function(d){
        // console.log(' l+' + d);
        if (lst.indexOf(d) < 0) {
            document.getElementById(d).style.display = 'none';
        } else {
            document.getElementById(d).style.display = null;
            // console.log(' l=' + d);
        }
    })
    // console.log('s: ' + s)
    if (s == "posts" || s == "settings") {
      document.getElementById('tremolaTitle').style.display = 'none';
      document.getElementById('conversationTitle').style.display = null;
      // document.getElementById('plus').style.display = 'none';
    } else {
      document.getElementById('tremolaTitle').style.display = null;
      // if (s == "connex") { /* document.getElementById('plus').style.display = 'none'; */}
      // else { /* document.getElementById('plus').style.display = null; */}
      document.getElementById('conversationTitle').style.display = 'none';
    }
    if (lst.indexOf('div:qr') >= 0) { prev_scenario = s; }
    curr_scenario = s;
    if (['chats', 'contacts', 'connex'].indexOf(curr_scenario) >= 0) {
      var cl = document.getElementById('btn:'+curr_scenario).classList;
      cl.toggle('active', true);
      cl.toggle('passive', false);
    }
  }
}

function btnBridge(e) {
  var e = e.id, m = '';
  if (['btn:chats','btn:posts','btn:contacts','btn:connex'].indexOf(e) >= 0)
    { setScenario(e.substring(4)); }
  if (e == 'btn:menu') {
    if (scenarioMenu[curr_scenario].length == 0)
      return;
    document.getElementById("menu").style.display = 'initial';
    document.getElementById("overlay-trans").style.display = 'initial';
    scenarioMenu[curr_scenario].forEach(function(e){
      m += "<button class=menu_item_button ";
      m += "onclick='" + e[1] + "();'>" + e[0] + "</button><br>";
    })
    m = m.substring(0, m.length-4);
    // console.log(curr_scenario + ' menu! ' + m);
    document.getElementById("menu").innerHTML = m;
    return;
  }
  // if (typeof Android != "undefined") { Android.onFrontendRequest(e); }
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

function closeOverlay(){
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
  document.getElementById('new_contact_discovery-overlay').style.display = 'none';
  document.getElementById('old_contact-overlay').style.display = 'none';
  overlayIsActive = false;
}

function showPreview() {
  var draft = escapeHTML(document.getElementById('draft').value);
  if (draft.length == 0) return;
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

function menu_look_up() {
  closeOverlay()
  document.getElementById('new_contact_discovery-overlay').style.display = 'initial';
  document.getElementById('overlay-bg').style.display = 'initial';
  // document.getElementById('chat_name').focus();
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
  if (curr_scenario == 'chats') {
    menu_new_conversation();
  } else if (curr_scenario == 'contacts') {
    menu_new_contact();
  } else if (curr_scenario == 'connex') {
    menu_new_pub();
  }
}

function launch_snackbar(txt) {
  var sb = document.getElementById("snackbar");
  sb.innerHTML = txt;
  sb.className = "show";
  setTimeout(function(){ sb.className = sb.className.replace("show", ""); }, 3000);
}

// --- QR display and scan

function showQR(){
  generateQR('did:ssb:ed25519:' + myId.substring(1).split('.')[0])
}

function generateQR(s)
{
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
              correctLevel : QRCode.CorrectLevel.M // L, M, Q, H
            };
    qr = new QRCode(e, arg);
  } else {
    qr.clear();
    qr.makeCode(s);
  }
  overlayIsActive = true;
}

function qr_scan_start () {
  // test if Android is defined ...
  backend("qrscan.init");
  closeOverlay();
}

function qr_scan_success(s) {
  closeOverlay();
  var t = "did:ssb:ed25519:";
  if (s.substring(0,t.length) == t) {
    s = '@' + s.substring(t.length) + '.ed25519';
  }
  var b = '';
  try {
      b = atob(s.substr(1, s.length-9));
      // FIXME we should also test whether it is a valid ed25519 public key ...
  } catch(err) {}
  if (b.length != 32) {
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
  var i = (a + "?").substring(0,1).toUpperCase()
  var c = {"alias": a, "initial": i, "color": colors[Math.floor(colors.length * Math.random())]};
  tremola.contacts[s] = c;
  persist();
  backend("add:contact " + s + " " + btoa(a))
  load_contact_item([s,c]);
  closeOverlay();
}

/**
 * Check that entered ShortName follows the correct pattern.
 * Lower cases are accepted, and the minus in 6th position is optional
 */
function look_up(shortname) {
  shortname = shortname.toUpperCase()
  if (shortname.search("^[A-Z0-9]{5}[A-Z0-9]{5}$") !== -1)
    shortname = shortname.slice(0, 5) + '-' + shortname.slice(5, 10)
  if (shortname.search("^[A-Z0-9]{5}-[A-Z0-9]{5}$") !== -1) {
    closeOverlay()
    backend("look_up " + shortname);
  } else {
    launch_snackbar(shortname + " is not a valid Shortname")
  }
}

// ---