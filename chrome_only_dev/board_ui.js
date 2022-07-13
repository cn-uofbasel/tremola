//board_ui.js

"use strict";

const Color = {
  BLACK: 'black',
  RED: 'red',
  GREEN: 'green',
  BLUE: 'blue',
  YELLOW: 'yellow',
  CYAN: 'cyan',
  MAGENTA: 'magenta',
  ORANGE: 'orange'
}

function load_board_list() {
  document.getElementById('lst:board_list').innerHTML = '';
  var bid;
  for (bid in tremola.board) {
    var cl, mem, item, bg, row, badge, badgeId, cnt;
    cl = document.getElementById('lst:board_list');
    mem = recps2display(tremola.board[bid].members)
    item = document.createElement('div');
    item.style = "padding: 0px 5px 10px 5px; margin: 3px 3px 6px 3px;";
    if (tremola.board[bid].forgotten) bg = ' gray'; else bg = ' light';
    row  = "<button class='board_item_button w100" + bg + "' onclick='load_board(\"" + bid + "\");' style='overflow: hidden; position: relative;'>";
    row += "<div style='white-space: nowrap;'><div style='text-overflow: ellipsis; overflow: hidden;'>" + tremola.board[bid].name + "</div>";
    row += "<div style='text-overflow: clip; overflow: ellipsis;'><font size=-2>" + escapeHTML(mem) + "</font></div></div>";
    badgeId = bid + "-badge"
    badge= "<div id='" + badgeId + "' style='display: none; position: absolute; right: 0.5em; bottom: 0.9em; text-align: center; border-radius: 1em; height: 2em; width: 2em; background: var(--red); color: white; font-size: small; line-height:2em;'>&gt;9</div>";
    row += badge + "</button>";
    row += ""
    item.innerHTML = row;
    cl.append(item);
  }
}

function load_board(bid) { //switches scene to board and changes title to board name
  curr_board = bid
  var b = tremola.board[bid]

  var title = document.getElementById("conversationTitle"), bg, box;
  title.style.display = null;
  title.classList = bid.forgotten ? ['gray'] : []
  box  = "<div style='white-space: nowrap;'><div style='text-overflow: ellipsis; overflow: hidden;text-align: center;'><font size=+2><strong>" + "Board: " + escapeHTML(b.name) + "</strong></font></div>";
  box += "<div style='color: black; text-overflow: ellipsis; overflow: hidden;text-align: center;'>" + escapeHTML(recps2display(b.members)) + "</div></div>";
  title.innerHTML = box;

  document.getElementById("div:columns_container").innerHTML = "" //clear old content

  //document.getElementById("div:columns_container").innerHTML = "<div class='column_wrapper' style='order: 100000;'><button class='board_item_button w100' onclick='menu_edit(\"board_new_column\", \"Enter name of new List: \", \"\")' style='overflow: hidden; position: relative;'>New List</button></div>"
  setScenario('board')
  document.getElementById("tremolaTitle").style.display = 'none';
  document.getElementById("tremolaTitle").style.position = 'fixed';
  title.style.display = null;

  load_all_columns()
  load_all_items()
}

function ui_update_Board(bid, old_state) {
  var board = tremola.board[bid]
  for(var i in board.columns) {
    var old_column = old_state.columns[i]
    var new_column = board.columns[i]

    if(!old_column) {
      load_column(i)
      return
    }
    if(new_column.removed && (old_column.removed != new_column.removed)) {
      ui_remove_column(i)
      return
    }
    if(old_column.name != new_column.name)
      ui_rename_column(i, new_column.name)
  }

  for(var i in board.items) {
    var old_item = old_state.items[i]
    var new_item = board.items[i]

    if(!old_item) {
      load_item(i)
      return
    }
    if(new_item.removed && (old_item.removed != new_column.removed)) {
      ui_remove_item(i)
      return
    }
    if(old_item.name != new_item.name)
      ui_update_item_name(i, new_item.name)
    if(old_item.description != new_item.description)
      ui_update_item_description(i, new_item.description)
    if(!equalArrays(old_item.assignees, new_item.assignees))
      ui_update_item_assignees(i)
    if(!equalArrays(old_item.comments, new_item.comments))
      ui_item_update_chat(i)
    if(old_item.curr_column != new_item.curr_column)
      ui_update_item_move_to_column(i, new_item.curr_column, new_item.position)
    if(old_item.color != new_item.color)
      ui_update_item_color(i, new_item.color)
  }
}

function equalArrays(array1, array2) {
  if(array1.length != array2.length)
    return false

  for(var i in array1) {
    if(array1[i] instanceof Array && array2[i] instanceof Array) {
      if(!equalArrays(array1[i], array2[i]))
        return false
    } else if(array1[i] != array2[i]) {
      return false
    }
  }
  return true
}

function close_board_context_menu() {
  if(curr_context_menu) {
    var context_menu = document.getElementById(curr_context_menu)
      if(context_menu)
        context_menu.style.display = 'none';
  }
  curr_context_menu = null
}

function menu_history() {
  closeOverlay()
  document.getElementById('overlay-bg').style.display = 'initial';
  document.getElementById('menu_history_content').innerHTML = ''
  overlayIsActive = true;
  var board = tremola.board[curr_board]

  var reversedHistory = [...board.history].reverse()

  for(var i in reversedHistory) {

    var author_name = tremola.contacts[reversedHistory[i][0]].alias
    var author_color = tremola.contacts[reversedHistory[i][0]].color
    var author_initial = tremola.contacts[reversedHistory[i][0]].initial

    var entryHTML = "<div class='w100' style='padding: 5px 5px 5px;overflow:auto;'>"
    entryHTML += "<button class=contact_picture style='float:left;margin-right: 0.75em; background: " + author_color + ";'>" + author_initial + "</button>"
    entryHTML += "<div class='chat_item_button light' style='display: table;float:right;overflow: hidden; width: calc(100% - 4.4em);word-break: break-word;margin-right:10px'>"
    entryHTML += "<div style='display: table-cell;padding-left:10px;vertical-align: middle;overflow-wrap: break-word;'>" + reversedHistory[i][1] + "</div>"
    entryHTML += "</div></div>"

    document.getElementById('menu_history_content').innerHTML += entryHTML

  }
  document.getElementById('div:menu_history').style.display = 'initial'
}

function menu_new_board() {
  fill_members();
  prev_scenario = 'board_list';
  setScenario("members");

  document.getElementById("div:textarea").style.display = 'none';
  document.getElementById("div:confirm-members").style.display = 'flex';
  document.getElementById("tremolaTitle").style.display = 'none';
  var c = document.getElementById("conversationTitle");
  c.style.display = null;
  c.innerHTML = "<font size=+1><strong>Create New Board</strong></font><br>Select up to 7 members";
  document.getElementById('plus').style.display = 'none';
}

function new_board() {
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
  menu_edit('new_board', 'Enter the name of the new board', '')
}

/*
  Columns
*/
function menu_new_column() {
  menu_edit("board_new_column", "Enter name of new List: ", "")
}

function load_column(columnID) {
  var board = tremola.board[curr_board]

  if(document.getElementById(columnID + "-columnWrapper")) {
    document.getElementById(columnID + "-columnWrapper").outerHTML = ""
  }

  if(board.columns[columnID].removed) {
    return
  }

  var column_name = board.columns[columnID].name
  var column_position = board.columns[columnID].position

  var columnsHTML = "<div class='column_wrapper' id='" + columnID + "-columnWrapper' style='margin-left:5px;order:" + column_position + ";'>"
  columnsHTML += "<div class='column light'>"
  columnsHTML += "<div class='column_hdr'>"
  columnsHTML += "<div style='float: left;max-width: 70%;margin-left:10px;'><b>" + column_name + "</b></div>"
  columnsHTML += "<div class='dropdown_menu' style='float: right;margin-right:5px;'>"
  columnsHTML += "<div onclick='context_menu_column_options(\""+ columnID + "\")' style='float: right;font-weight: bold;'>...</div>"
  columnsHTML += "<div class='context_menu' id='context_options-" + columnID +"'> "
  columnsHTML += "</div></div></div>"
  columnsHTML += "<div style='overflow:auto;max-height:60vh;'><div class='column_content' id='" + columnID + "-columnContent'></div></div>"
  //columnsHTML += "<div style='order: 100000;width: 95%; margin: auto;'><button class='board_item_button w100' onclick='menu_create_column_item(\"" + columnID +"\")' style='overflow: hidden; position: relative;'>New Card</button></div>"
  columnsHTML += "<div class='column_footer' onclick='menu_create_item(\"" + columnID +"\")'>+ Add new card</div>"
  columnsHTML +="</div></div>"

  document.getElementById("div:columns_container").innerHTML += columnsHTML

}

function load_all_columns() {
  var board = tremola.board[curr_board]
  for (var i in board.columns) {
    load_column(i)
  }
}

function context_menu_column_options(columnID) {
  close_board_context_menu()
  document.getElementById("overlay-trans").style.display = 'initial';
  var context_menu = document.getElementById('context_options-' + columnID)
  context_menu.style.display = 'block'
  curr_context_menu = 'context_options-' + columnID
  context_menu.innerHTML = "<button class='context_options_btn' onclick='menu_create_item(\"" + columnID + "\")'>Add new card</button>"
  context_menu.innerHTML += "<button class='context_options_btn' onclick='contextmenu_move_column(\"" + columnID + "\")'>Move List...</button>"
  context_menu.innerHTML += "<button class='context_options_btn' onclick='menu_rename_column(\"" + columnID + "\")'>Rename...</button>"
  context_menu.innerHTML += "<button class='context_options_btn' onclick='btn_remove_column(\""+ columnID + "\")' style='color:red;'>Delete</button>"
  overlayIsActive = true
}

function contextmenu_move_column(columnID) {
  document.getElementById('context_options-' + columnID).innerHTML = ''
  var board = tremola.board[curr_board]
  var availablePos = Array.from(Array(board.numOfActiveColumns), (_, i) => i+1)
  availablePos.splice(availablePos.indexOf(board.columns[columnID].position), 1)
  var menuHTML = "<button class='context_options_btn' onclick='context_menu_column_options(\"" + columnID + "\")'> ... </button>"

  for (var i in availablePos) {
    menuHTML += "<button class='context_options_btn' onclick='ui_move_column(\"" + columnID + "\",\""+ availablePos[i] +"\")'>"+ availablePos[i] +"</button>"
  }

  document.getElementById('context_options-' + columnID).innerHTML = menuHTML
}

function menu_rename_column(columnID) {
  curr_column = columnID
  menu_edit('board_rename_column', 'Enter new name: ', tremola.board[curr_board].columns[columnID].name)
}

function btn_remove_column (columnID) {
  removeColumn(curr_board, columnID)
  closeOverlay()
}

function ui_rename_column(columnID, new_name) {
  var wrapper = document.getElementById(columnID + '-columnWrapper')

  if(!wrapper) {
    load_column(columnID)
    return
  }

  wrapper.getElementsByClassName('column_hdr')[0].getElementsByTagName('b')[0].innerHTML = new_name
}

function ui_remove_column(columnID) {
  var board = tremola.board[curr_board]

  if(!document.getElementById(columnID + "-columnWrapper"))
    return
  document.getElementById(columnID + "-columnWrapper").outerHTML = "" //remove column from ui
}

function ui_move_column(columnID, insertPos) {
  closeOverlay()
  insertPos = parseInt(insertPos)
  var board = tremola.board[curr_board]
  var oldPos = board.columns[columnID].position
  if (oldPos == insertPos) // column is already on given position
    return

  for(var i in board.columns) {
    var curr_column = board.columns[i]

    if(curr_column.removed)
      continue

    if(i == columnID)
      continue

    if(oldPos > insertPos) {
      if(curr_column.position < insertPos){
        continue
      } else if(curr_column.position > board.columns[columnID].position) {
        continue
      } else {
        document.getElementById(i + "-columnWrapper").style.order = ++curr_column.position
      }
    } else {
      if(curr_column.position < board.columns[columnID].position) {
        continue
      } else if(curr_column.position > insertPos) {
        continue
      } else {
        document.getElementById(i + "-columnWrapper").style.order = --curr_column.position
      }
    }

  }
  document.getElementById(columnID+'-columnWrapper').style.order = insertPos
  board.columns[columnID].position = insertPos
  persist()
}

/*
  Items
*/

function menu_create_item(columnID) {
  curr_column = columnID
  menu_edit('board_new_item', 'Enter name of new Card', '')
}

function load_item(itemID) {
  var board = tremola.board[curr_board]

  var name = board.items[itemID].name
  var pos = board.items[itemID].position
  var columnID = board.items[itemID].curr_column
  var color = board.items[itemID].color

  if(document.getElementById(itemID + "-item")) {
    document.getElementById(itemID + "-item").outerHTML = ""
  }

  if(board.items[itemID].removed || board.columns[columnID].removed)
    return


  var itemHTML = "<div class='column_item' style='order:" + pos + ";overflow: auto;' id='"+ itemID +"-item' onclick='item_menu(\"" + itemID + "\")'>" //board_item_button
  //itemHTML += "<button class='item_button' onclick='item_menu(\"" + itemID + "\")'>"
  itemHTML += "<div style='padding-top: 10px; padding-left: 10px; padding-bottom: 10px'> <b><font id='"+ itemID +"-itemHdr' color='" + color +"'>" + name + "</font></b></div>"
  itemHTML += "<div id='"+ itemID +"-itemDescr' style='font: 12px Helvetica Neue, sans-serif; color: #808080;overflow-wrap: break-word;max-height: 4.8em;overflow: hidden;padding-left: 10px;padding-right: 10px;'>" + board.items[itemID].description + "</div>"
  itemHTML += "<div id='"+ itemID +"-itemAssignees' style='padding-left: 10px;padding-right: 10px;display:flex;justify-content: flex-start;flex-direction: row;padding-bottom:10px;padding-top:5px;'>"

  for(var i in board.items[itemID].assignees) {
    var assigneeID = board.items[itemID].assignees[i]
    var assigneeInitial = tremola.contacts[assigneeID].initial
    var assigneeColor = tremola.contacts[assigneeID].color

    itemHTML += "<div style=';padding-left:3px;overflow: auto;'>"
    itemHTML += "<button class=contact_picture style='height: 1.5em; width: 1.5em; box-shadow: none; background: " + assigneeColor + ";'>" + assigneeInitial + "</button>"
    itemHTML += "</div>"
  }

  itemHTML += "</div>" //</button>

  document.getElementById(columnID + "-columnContent").innerHTML += itemHTML

}

function load_all_items() {
  var board = tremola.board[curr_board]
  for (var i in board.items) {
    load_item(i)
  }
}

function item_menu(itemID) {
  closeOverlay()
  curr_rename_item = itemID
  curr_item = itemID
  document.getElementById('overlay-bg').style.display = 'initial';
  overlayIsActive = true;

  var item = tremola.board[curr_board].items[itemID]
  document.getElementById('div:item_menu').style.display = 'initial'
  document.getElementById('btn:item_menu_description_save').style.display = 'none'
  document.getElementById('btn:item_menu_description_cancel').style.display = 'none'
  document.getElementById('item_menu_comment_text').value = ''
  document.getElementById('item_menu_title').innerHTML = "<font id='"+ itemID +"-itemMenuHdr' color='" + item.color + "'>" + item.name + "</font"

  //load description
  var descDiv = document.getElementById('div:item_menu_description')
  document.getElementById('div:item_menu_description_text').value = item.description

  document.getElementById('div:item_menu_description_text').addEventListener('focus', (event) => {
             document.getElementById('btn:item_menu_description_save').style.display = 'initial'
             document.getElementById('btn:item_menu_description_cancel').style.display = 'initial'
             event.target.style.border = "solid 2px black"
             document.getElementById('div:item_menu_description_text').removeEventListener('focus', event)
          });

  //load chat
  var draftDiv = document.getElementById('div:item_menu_comments')
  var commentsHTML = ''
  var commentsList = [...item.comments].reverse()
  for(var i in commentsList) {
    var author_name = tremola.contacts[commentsList[i][0]].alias
    var author_color = tremola.contacts[commentsList[i][0]].color
    var author_initial = tremola.contacts[commentsList[i][0]].initial

    commentsHTML += "<div class='w100' style='padding: 5px 5px 5px;overflow:auto;'>"
    commentsHTML += "<button class=contact_picture style='float:left;margin-right: 0.75em; background: " + author_color + ";'>" + author_initial + "</button>"
    commentsHTML += "<div class='chat_item_button light' style='display: table;float:right;overflow: hidden; width: calc(100% - 4.4em);word-break: break-word;margin-right:10px'>"
    commentsHTML += "<div style='display: table-cell;padding-left:10px;vertical-align: middle;overflow-wrap: break-word;'>" + commentsList[i][1] + "</div>"
    commentsHTML += "</div></div>"
  }
  document.getElementById('lst:item_menu_posts').innerHTML = commentsHTML

  //load assignees display
  var assigneesDiv = document.getElementById('div:item_menu_assignees')
  var order = 0;
  var assigneesHTML = ""
  for(var i in item.assignees) {
    var author_color = tremola.contacts[item.assignees[i]].color
    var author_initial = tremola.contacts[item.assignees[i]].initial
    assigneesHTML += "<button class=contact_picture style='float:left;margin-right: 0.75em; background: " + author_color + ";'>" + author_initial + "</button>"
  }
  document.getElementById("div:item_menu_assignees").innerHTML = assigneesHTML
}

function item_menu_save_description() {
  var new_description = document.getElementById('div:item_menu_description_text').value
  var item = tremola.board[curr_board].items[curr_item]

  if(item.description != new_description) {
    setItemDescription(curr_board, curr_item, new_description)
  }
  document.getElementById('btn:item_menu_description_save').style.display = 'none'
  document.getElementById('div:item_menu_description_text').style.border = 'none'
  document.getElementById('btn:item_menu_description_cancel').style.display = 'none'
}

function item_menu_cancel_description() {
  document.getElementById('div:item_menu_description_text').style.border = 'none'
  document.getElementById('div:item_menu_description_text').value = tremola.board[curr_board].items[curr_item].description
  document.getElementById('btn:item_menu_description_save').style.display = 'none'
  document.getElementById('btn:item_menu_description_cancel').style.display = 'none'
}

function contextmenu_change_column() {
  close_board_context_menu()
  document.getElementById("overlay-trans").style.display = 'initial';

  var board = tremola.board[curr_board]
  var OptionHTML ="<div class='context_menu' id='context_options-" + curr_item + "-changeColumn'>"

  if(board.numOfActiveColumns <= 1) {
    launch_snackbar("There is only one list")
  }

  var columnPositionList = Object.keys(board.columns).map((key) => [key, board.columns[key].position])
  columnPositionList.sort((a, b) => a[1] - b[1])

  for(var i in columnPositionList) {
    var columnID =  columnPositionList[i][0]
    if((board.items[curr_item].curr_column == columnID) || (board.columns[columnID].removed) )
      continue

    OptionHTML += "<button class='context_options_btn' onclick='btn_move_item(\""+ curr_item + "\",\""+ columnID +"\")'>"+ board.columns[columnID].name +"</button>"
  }
  OptionHTML += "</div>"

  curr_context_menu = 'context_options-' + curr_item + "-changeColumn"
  document.getElementById("change_column_options").innerHTML = OptionHTML
  document.getElementById(curr_context_menu).style.display = 'block'
  overlayIsActive = true
}

function btn_move_item(item, new_column) {
  moveItem(curr_board, item, new_column)

  close_board_context_menu()
}

function btn_remove_item() {
  removeItem(curr_board, curr_item)
  closeOverlay()
}

function contextmenu_item_change_position() {
  close_board_context_menu()

  var board = tremola.board[curr_board]
  var itemList = board.columns[board.items[curr_item].curr_column].item_ids
  var itemPosList = []

  for(var i in itemList) {
    var item = board.items[itemList[i]]

    if (item.removed)
      continue
    if (item.id == board.items[curr_item].id)
      continue

    itemPosList.push([i, item.position])
  }
  itemPosList.sort((a, b) => a[1] -b[1])
  console.log(itemPosList)
  var posHTML = "<div class='context_menu' id='context_options-" + curr_item + "-changePosition'>"
  for(var i in itemPosList) {
    posHTML += "<button class='context_options_btn' onclick='ui_change_item_order(\""+ curr_item + "\",\""+ itemPosList[i][1] +"\")'>"+ itemPosList[i][1] +"</button>"
  }
  curr_context_menu = 'context_options-' + curr_item + "-changePosition"
  document.getElementById("change_position_options").innerHTML = posHTML
  document.getElementById(curr_context_menu).style.display = 'block'
  overlayIsActive = true
}

function contextmenu_item_assign() {
  close_board_context_menu()
  var board = tremola.board[curr_board]

  var aliasList = []

  for (var m in board.members) {
    aliasList.push([tremola.contacts[board.members[m]].alias, board.members[m]])
  }
  aliasList.sort()

  var assignHTML = "<div class='context_menu' id='context_options-" + curr_item + "-assign'>"
  for (var i in aliasList) {
    var alias = aliasList[i][0]
    var fid = aliasList[i][1]
    var assigned = board.items[curr_item].assignees.indexOf(fid) >= 0
    var color = assigned ? "background-color: #17cf32;" : "";
    console.log(assigned)
    console.log(color)

    assignHTML += "<button class='context_options_btn' style='"+ color+"' onclick='ui_item_assign(\""+ fid + "\")'>" + alias + "</button>"
  }
  curr_context_menu = 'context_options-' + curr_item + "-assign"
  document.getElementById("assign_options").innerHTML = assignHTML
  document.getElementById(curr_context_menu).style.display = 'block'
  overlayIsActive = true
}

function btn_post_comment() {
  var comment = document.getElementById('item_menu_comment_text').value
  if(comment == '')
    return

  postItemComment(curr_board, curr_item, comment)
  item_menu(curr_item)
}

function btn_rename_item() {

}

function ui_change_item_order(itemID, newPos) {
  close_board_context_menu()
  newPos = parseInt(newPos)
  var board = tremola.board[curr_board]
  var oldPos = board.items[itemID].position
  var column = board.columns[board.items[itemID].curr_column]

    if (oldPos == newPos) // column is already on given position
      return

    for(var i of column.item_ids) {
      var curr_item = board.items[i]

      if(curr_item.removed)
        continue

      if(i == itemID)
        continue

      if(oldPos > newPos) {
        if(curr_item.position < newPos){
          continue
        } else if(curr_item.position > board.items[itemID].position) {
          continue
        } else {
          document.getElementById(i + "-item").style.order = ++curr_item.position
        }
      } else {
        if(curr_item.position < board.items[itemID].position) {
          continue
        } else if(curr_item.position > newPos) {
          continue
        } else {
          document.getElementById(i + "-item").style.order = --curr_item.position
        }
      }

    }
    document.getElementById(itemID+'-item').style.order = newPos
    board.items[itemID].position = newPos
    persist()
}

function ui_remove_item(itemID) {
  var board = tremola.board[curr_board]
  var column = board.columns[board.items[itemID].curr_column]

  for (var i in column.item_ids) {
    var curr_item = board.items[column.item_ids[i]]
    if(curr_item.position > board.items[itemID].position) {
      curr_item.position--
      document.getElementById(curr_item.id + '-item').style.order = curr_item.position
    }
  }

  document.getElementById(itemID + '-item').outerHTML = ""
}

function ui_item_assign(fid) {
  //close_board_context_menu()
  var board = tremola.board[curr_board]
  var alreadyAssigned = board.items[curr_item].assignees.includes(fid)

  if(alreadyAssigned)
    unassignFromItem(curr_board, curr_item, fid)
  else
    assignToItem(curr_board, curr_item, fid)
}

function ui_move_item(itemID, old_pos) {
  var board = tremola.board[curr_board]
  var item = board.items[itemID]
  var old_column = board.columns[item.curr_column]

  document.getElementById(itemID+'-item').style.order = item.position
  for (var i in old_column.item_ids) {
    if(board.items[old_column.item_ids[i]].position > old_pos)
      document.getElementById(old_column.item_ids[i]+'-item').style.order = --board.items[old_column.item_ids[i]].position
  }
  load_item(curr_op.body.cmd[1])
}

function contextmenu_change_color() {
  close_board_context_menu()
  var board = tremola.board[curr_board]
  var item = board.items[curr_item]

  var colors = Object.values(Color)
  colors.splice(colors.indexOf(item.color), 1)

  var colorHTML = "<div class='context_menu' id='context_options-" + curr_item + "-color'>"

  for (var c of colors) {
    colorHTML += "<button class='context_options_btn' onclick='btn_change_item_color(\""+ curr_item +"\", \"" + c + "\")'>"+ c +"</button>"
  }

  curr_context_menu = 'context_options-' + curr_item + "-color"
  document.getElementById("change_color_options").innerHTML = colorHTML
  document.getElementById(curr_context_menu).style.display = 'block'
  overlayIsActive = true
}

function btn_change_item_color(iid, color) {
  close_board_context_menu()
  setItemColor(curr_board, iid, color)
}

function ui_update_item_name(itemID, new_name) {
  var hdr = document.getElementById(itemID +'-itemHdr')

  if(hdr) {
    hdr.innerHTML = new_name
  } else {
    load_item(itemID)
  }

  if(curr_item == itemID) {
    var itemMenuHdr = document.getElementById(itemID + '-itemMenuHdr')
    if(itemMenuHdr) {
      itemMenuHdr.innerHTML = new_name
    } else {
      item_menu(itemID)
    }
  }
}

function ui_update_item_color(itemID, new_color) {
  var hdr = document.getElementById(itemID +'-itemHdr')

  if(hdr) {
    hdr.style.color = new_color
  } else {
    load_item(itemID)
  }

  if(curr_item == itemID) {
    var itemMenuHdr = document.getElementById(itemID + '-itemMenuHdr')
    if(itemMenuHdr) {
      itemMenuHdr.style.color = new_color
    } else {
      item_menu(itemID)
    }
    if(curr_context_menu && curr_context_menu.split('-')[2] == 'color')
      contextmenu_change_color()
  }
}

function ui_update_item_description(itemID, new_descr) {
  var itemDescr = document.getElementById(itemID +'-itemDescr')


  if(itemDescr) {
    itemDescr.innerHTML = new_descr
  } else {
    load_item(itemID)
  }

  if(curr_item == itemID) {
    var itemMenuDescr = document.getElementById('div:item_menu_description_text')
    if(itemMenuDescr) {
      itemMenuDescr.value = new_descr
    } else {
      item_menu(itemID)
    }
  }
}

function ui_update_item_assignees(itemID) {
  var board = tremola.board[curr_board]
  var item = board.items[itemID]

  var assigneesWrapper = document.getElementById(itemID +'-itemAssignees')
  if(assigneesWrapper) {
    var assigneesHTML = ''
    for(var i in board.items[itemID].assignees) {
      var assigneeID = board.items[itemID].assignees[i]
      var assigneeInitial = tremola.contacts[assigneeID].initial
      var assigneeColor = tremola.contacts[assigneeID].color

      assigneesHTML += "<button class=contact_picture style='height: 1.5em; width: 1.5em; box-shadow: none; background: " + assigneeColor + ";'>" + assigneeInitial + "</button>"
    }
    assigneesWrapper.innerHTML = assigneesHTML
  } else {
    load_item(itemID)
  }

  if(curr_item == itemID) {
    var assigneesMenuWrapper = document.getElementById('div:item_menu_assignees')
    if(assigneesMenuWrapper) {
      var assigneesMenuHTML = ""
      for(var i in item.assignees) {
        var author_color = tremola.contacts[item.assignees[i]].color
        var author_initial = tremola.contacts[item.assignees[i]].initial
        assigneesMenuHTML += "<button class=contact_picture style='float:left;margin-right: 0.75em; background: " + author_color + ";'>" + author_initial + "</button>"
      }
      assigneesMenuWrapper.innerHTML = assigneesMenuHTML
    } else {
      item_menu(itemID)
    }
    if(curr_context_menu && curr_context_menu.split('-')[2] == 'assign') {
      contextmenu_item_assign()
    }
  }
}

function ui_item_update_chat(itemID) {
  var board = tremola.board[curr_board]
  var item = board.items[itemID]

  if(curr_item == itemID) {
    var chatWrapper = document.getElementById('lst:item_menu_posts')
    if(chatWrapper) {
      var commentsHTML = ''
      var commentsList = [...item.comments].reverse()
      for(var i in commentsList) {
        var author_name = tremola.contacts[commentsList[i][0]].alias
        var author_color = tremola.contacts[commentsList[i][0]].color
        var author_initial = tremola.contacts[commentsList[i][0]].initial

        commentsHTML += "<div class='w100' style='padding: 5px 5px 5px;overflow:auto;'>"
        commentsHTML += "<button class=contact_picture style='float:left;margin-right: 0.75em; background: " + author_color + ";'>" + author_initial + "</button>"
        commentsHTML += "<div class='chat_item_button light' style='display: table;float:right;overflow: hidden; width: calc(100% - 4.4em);word-break: break-word;margin-right:10px'>"
        commentsHTML += "<div style='display: table-cell;padding-left:10px;vertical-align: middle;overflow-wrap: break-word;'>" + commentsList[i][1] + "</div>"
        commentsHTML += "</div></div>"
      }
    chatWrapper.innerHTML = commentsHTML
    } else {
      item_menu(itemID)
    }
  }
}

function ui_update_item_move_to_column(itemID, columnID, newPos) {
  var itemHTML = document.getElementById(itemID + "-item").outerHTML
  document.getElementById(itemID + "-item").outerHTML = ''
  document.getElementById(columnID + "-columnContent").innerHTML += itemHTML
  document.getElementById(itemID +'-item').style.order = newPos

  if(curr_item == itemID) {
    if(curr_context_menu) {
      if(curr_context_menu.split('-')[2] == 'changePosition')
        contextmenu_item_change_position()
      else if(curr_context_menu.split('-')[2] == 'changeColumn')
        contextmenu_change_column()
    }
  }
}
