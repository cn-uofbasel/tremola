//board.js

"use strict";

var curr_board;
var curr_context_menu;
var curr_column;
var curr_item;

var curr_rename_item;

const Operation = {
  BOARD_CREATE: 'board/create',
  COLUMN_CREATE: 'column/create',
  ITEM_CREATE: 'item/create',
  COLUMN_REMOVE: 'column/remove',
  COLUMN_RENAME: 'column/rename',
  ITEM_REMOVE: 'item/remove',
  ITEM_RENAME: 'item/rename',
  ITEM_MOVE: 'item/moveTo',
  ITEM_SET_DESCRIPTION: 'item/setDiscription',
  ITEM_POST_COMMENT: 'item/post',
  ITEM_ASSIGN: 'item/assign',
  ITEM_UNASSIGN: 'item/unassign',
  ITEM_COLOR: 'item/color'
}


function createBoard(name, recps) {
  var data = {
               'bid': null,
               'cmd': [Operation.BOARD_CREATE, name],
               'prev': null
             }
  board_send_to_backend(data, recps)
}

function createColumn(bid, name) {
  var board = tremola.board[bid]
  var data = {
                'bid': bid,
                'cmd': [Operation.COLUMN_CREATE, name],
                'prev': board.curr_prev
             }
  board_send_to_backend(data, board.members)
}

function createColumnItem(bid, cid, name) {
  var board = tremola.board[bid]
  var data = {
                'bid': bid,
                'cmd': [Operation.ITEM_CREATE, cid, name],
                'prev': board.curr_prev
             }
  board_send_to_backend(data, board.members)
}

function removeColumn(bid, cid) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.COLUMN_REMOVE, cid],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function renameColumn(bid, cid, newName) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.COLUMN_RENAME, cid, newName],
                  'prev': board.curr_prev
             }
  board_send_to_backend(data, board.members)
}

function removeItem(bid, iid) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.ITEM_REMOVE, iid],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function renameItem(bid, iid, new_name) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.ITEM_RENAME, iid, new_name],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function moveItem(bid, iid, new_cid) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.ITEM_MOVE, iid, new_cid],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function setItemDescription(bid, iid, description) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.ITEM_SET_DESCRIPTION, iid, description],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function postItemComment(bid, iid, comment) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.ITEM_POST_COMMENT, iid, comment],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function assignToItem(bid, iid, assigned) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.ITEM_ASSIGN, iid, assigned],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function unassignFromItem(bid, iid, unassign) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.ITEM_UNASSIGN, iid, unassign],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function setItemColor(bid, iid, color) {
  var board = tremola.board[bid]
  var data = {
                  'bid': bid,
                  'cmd': [Operation.ITEM_COLOR, iid, color],
                  'prev': board.curr_prev
               }
  board_send_to_backend(data, board.members)
}

function board_send_to_backend(data, recps) {
  recps = recps.join(' ')
  data = unicodeStringToTypedArray(JSON.stringify(data))
  backend("priv:board " + btoa(data) + " " + recps);
}



function updateCurrPrev(bid, e) {
  var board = tremola.board[bid]
  var event_prev = e.body.prev
  console.log("EVENT_PREVS: " + event_prev)

  board.curr_prev.push(e.key.toString())  // Assumes the backend guarantees the correct order of events

  if(event_prev) {
    for(var i in event_prev) {
      var pos = board.curr_prev.indexOf(event_prev[i])
      console.log("PREV: " + event_prev[i])
      console.log("PREV_POS: " + pos)
      if(pos >= 0) {
        board.curr_prev.splice(pos, 1)
      }
    }
  }

  console.log("NEW PREV: " + board.curr_prev)
}

/*
    ScuttleSort
*/

function newOperation(bid, operationID) {
  console.log("NEW OPERATION: " + operationID)
  var board = tremola.board[bid]
  var op = board.operations[operationID]
  var prev = op.body.prev
  op['cycl'] = false
  op['succ'] = []
  op['rank'] = 0
  op['vstd'] = false
  op['sorted'] = true

  for(var i in prev) {
    let c = prev[i]
    console.log("C: " + c)
    let p = board.operations[c]
    if(p && p.sorted) {
      p.succ.push(operationID)
    } else {
      if(!(c in board.pendingOperations))
        board.pendingOperations[c] = []
      let a = board.pendingOperations[c]
      if(!a.includes(operationID))
        a.push(operationID)

    }
  }

  var pos = 0
  for(var i in prev) {
    let p = prev[i]
    if(p in board.operations && board.operations[p].sorted && board.operations[p].indx > pos)
      pos = board.operations[p].indx
  }

  for(var i= pos; i < board.sortedOperations.length; i++)
    board.operations[board.sortedOperations[i]].indx += 1
  op['indx'] = pos
  console.log(pos)
  board.sortedOperations.splice(pos, 0, operationID) // _insert

  var no_anchor = true
  for(var i in prev) {
    let p = prev[i]
    if(p in board.operations && board.operations[p].sorted) {
      add_edge_to_the_past(bid, operationID, p)
      no_anchor = false
    }
  }
  if(no_anchor && board.sortedOperations.length > 1)
    rise(bid, operationID)

  let s = board.pendingOperations[operationID]
  if(s) {
    for(let e of s) {
      let curr_op = board.operations[e]
      for(var i in curr_op.body.prev) {
        if(curr_op.body.prev[i] != operationID)
          continue
        add_edge_to_the_past(bid, e, operationID)
        op.succ.push(e)
      }
    }
    delete board.pendingOperations[operationID]
  }
}

function add_edge_to_the_past(bid, operationID, causeID) {
  console.log("add_edge_to_the_past")
  var board = tremola.board[bid]
  var op = board.operations[operationID]
  var cause = board.operations[causeID]

  let visited = new Set()
  cause.cycl = true
  visit(bid, operationID, cause.rank, visited)
  cause.cycl = false

  let si = op.indx
  let ci = cause.indx
  console.log("si: " + si + ", ci: "+ ci)
  if(si < ci)
    jump(bid, operationID, ci)
  else
    rise(bid, operationID)

  let a = Array.from(visited)
  a.sort((a,b) => {return b.indx - a.indx})
  for(let v of a) {
    rise(bid, v.key)
    v.vstd = false
  }
}

function rise(bid, operationID) {
  var board = tremola.board[bid]
  var op = board.operations[operationID]

  let len1 = board.sortedOperations.length -1
  let si = op.indx
  var pos = si
  while( pos < len1 && op.rank > board.operations[board.sortedOperations[pos+1]].rank)
    pos += 1
  console.log("OP-RANK: " + op.rank)
  console.log("OTHER-ID: " + board.sortedOperations[pos+1])
  console.log("OP-ID: " + operationID)
  console.log("POS: " +  pos)
  while( pos < len1 && op.rank == board.operations[board.sortedOperations[pos+1]].rank
                               && board.sortedOperations[pos+1] < operationID) {
       pos += 1
  }

  if(si < pos)
    jump(bid, operationID, pos)
}

function jump(bid, operationID, pos) {
  var board = tremola.board[bid]
  var op = board.operations[operationID]

  let si = op.indx
  for (var i = si +1; i < pos+1; i++)
    board.operations[board.sortedOperations[i]].indx -= 1
  moveOperation(bid, si, pos)
  op.indx = pos
}

function visit(bid, operationID, rnk, visited) {
  var board = tremola.board[bid]
  let out = [[operationID]];

  while (out.length > 0) {
      let o = out[out.length - 1];
      if (o.length == 0) {
          out.pop();
          continue
      }
      let c = board.operations[o.pop()];
      c.vstd = true;
      visited.add(c);
      if (c.cycl)
          throw new Error('cycle');
      if (c.rank <= (rnk + out.length - 1)) {
          c.rank = rnk + out.length;
          out.push(Array.from(c.succ));
      }
  }

}

function moveOperation(bid, from, to) {
  var board = tremola.board[bid]
  let h = board.sortedOperations[from]
  board.sortedOperations.splice(from, 1);
  board.sortedOperations.splice(to, 0, h);
}



function reload_curr_board() {
  if(curr_board)
    board_reload(curr_board)
}

function board_reload(bid) {
  var board = tremola.board[bid]
  board.columns = {}
  board.numOfActiveColumns = 0
  board.items = {}
  board.pendingOperations = {}
  board.sortedOperations = []

  for(var op in board.operations) {
    delete board.operations[op].indx
    delete board.operations[op].succ
    delete board.operations[op].rank
    delete board.operations[op].vstd
    delete board.operations[op].cycl
    board.operations[op].sorted = false
  }

  for(var op in board.operations) {
    newOperation(bid, op)
  }
  apply_all_operations(bid)

  if(curr_scenario == 'board' && curr_board == bid) {
    closeOverlay()
    curr_item = null
    curr_column = null
    curr_context_menu = null
    load_board(bid)
  }


}






// sort events with Kahn's algorithm
// sorted_list[0] contains the hash value of the first operation (according to the logical clock)
/*
function sortOperations(bid) {
  var board = tremola.board[bid]
  var sorted_list = [] // sorted hash values of board operations
  var operation_list = board['operations']

  var sources // hash values of events which are not pointed to by a prev-pointer
  while((sources = getSources(tremola.board[bid]['operations'], sorted_list)).length != 0) {
    var max_hash = ''
    if(sources.length > 1) { // Concurrency conflict resolving via max hash rule
      for (var i in sources) {
        if(sources[i] > max_hash)
          max_hash = sources[i]
      }
    } else {
      max_hash = sources[0]
    }
    sorted_list.push(max_hash.toString())
  }
  sorted_list.reverse()
  return sorted_list
}

//returns events which are not pointed to by a prev-pointer
function getSources(events, already_sorted) {
  var sources = []

  for(var i in events) {
    var hasIngoingDependencies = false
    if(!(already_sorted.includes(i))) {
      for(var j in events) {
        if(i != j && !(already_sorted.includes(j)) && events[j].body.prev) {
          if(Object.values(events[j].body.prev).includes(i)) {
            hasIngoingDependencies = true
            break
          }
        }
      }
      if(!hasIngoingDependencies) {
        sources.push(i)
      }
    }
  }

  return sources
}
*/


// returns true if both given pointers point to the same operations
function comparePrevs(prev1, prev2) {
  for(var i in prev1) {
    if(!(i in prev2))
      return false
    if(prev1[i] != prev2[i])
      return false
  }

  for(var i in prev2) {
      if(!(i in prev1))
        return false
      if(prev1[i] != prev2[i])
        return false
  }
  return true
}

/*
  Functions that manage local storage:
*/

function apply_all_operations(bid) {
  var board = tremola.board[bid]
  board.history = []

  var old_state = JSON.parse(JSON.stringify(board));
  console.log(old_state)
  //execute operations and save results to local storage
  for(var i in board.sortedOperations) {
    apply_operation(bid, board.sortedOperations[i])
  }
  console.log(board)

  if(curr_board == bid) { // update ui
    ui_update_Board(bid, old_state)
  }
}
/*
function apply_operation_from_pos(bid, pos) {
  var board = tremola.board[bid]
  var old_state = JSON.parse(JSON.stringify(board));

  board.history.splice(pos, board.history.length - pos)

  for (var i = pos; i < board.sortedOperations.length; i++) {
    apply_operation(bid, board.sortedOperations[i])
  }

  if(curr_board == bid) { // update ui
      ui_update_Board(bid, old_state)
   }
}
*/

function apply_operation(bid, operationID, apply_on_ui = false) {
  var board = tremola.board[bid]
  var curr_op = board['operations'][operationID]

    console.log('curr_op', curr_op);
  var author_name = tremola.contacts[curr_op.fid].alias
  var historyMessage = author_name + " "

  switch(curr_op.body.cmd[0]){
    case Operation.BOARD_CREATE:
      historyMessage += "created the board \"" + curr_op.body.cmd[1] + "\""
      board.name = curr_op.body.cmd[1]
      break
    case Operation.COLUMN_CREATE:
      historyMessage += "created the list \"" + curr_op.body.cmd[1] +"\""
      var newPos = 0
      if(curr_op.key in board.columns) {
        if(board.columns[curr_op.key].removed)
          break
        newPos = board.columns[curr_op.key].position
      } else
        newPos = ++board.numOfActiveColumns

      board.columns[curr_op.key] = {'name': curr_op.body.cmd[1],
                                    'id': curr_op.key.toString(),
                                    'item_ids': [],
                                    'position': newPos,
                                    'numOfActiveItems': 0,
                                    'removed': false
                                   }
      if(apply_on_ui)
        load_column(curr_op.key)
      break
    case Operation.COLUMN_REMOVE:
      historyMessage += "removed list \""+ board.columns[curr_op.body.cmd[1]].name + "\""
      board.columns[curr_op.body.cmd[1]].removed = true

      for (var i in board.columns) {
          if(board.columns[i].removed)
              continue

          if(board.columns[i].position > board.columns[curr_op.body.cmd[1]].position) {
            --board.columns[i].position
          }

        }
      board.numOfActiveColumns--

      if(apply_on_ui)
        ui_remove_column(curr_op.body.cmd[1])
      break
    case Operation.COLUMN_RENAME:
      historyMessage += "renamed list \"" + board.columns[curr_op.body.cmd[1]].name + "\" to \"" + curr_op.body.cmd[2] + "\""
      if (!(curr_op.body.cmd[1] in board.columns))
        break
      board.columns[curr_op.body.cmd[1]].name = curr_op.body.cmd[2]
      if(apply_on_ui)
        ui_rename_column(curr_op.body.cmd[1], curr_op.body.cmd[2])
      break
    case Operation.ITEM_CREATE:
      historyMessage += "created a card in list \""+ board.columns[curr_op.body.cmd[1]].name + "\" with the name: \"" + curr_op.body.cmd[2] + "\""
      var newPos = 0
      if (!(curr_op.body.cmd[1] in board.columns))
        break
      if(curr_op.key in board.items) {
        if(board.items[curr_op.key].removed) {
          break
        }
        newPos = board.items[curr_op.key].position
      } else {
        newPos = ++board.columns[curr_op.body.cmd[1]].numOfActiveItems
      }

      board.items[curr_op.key] = {'name': curr_op.body.cmd[2],
                                  'id': curr_op.key.toString(),
                                  'curr_column': curr_op.body.cmd[1],
                                  'assignees': [],
                                  'comments': [],
                                  'description': "",
                                  'position': newPos,
                                  'color': Color.BLACK,
                                  'removed': false
                                }
      board.columns[curr_op.body.cmd[1]].item_ids.push(curr_op.key.toString())
      if(apply_on_ui)
        load_item(curr_op.key)
      break
    case Operation.ITEM_REMOVE:
      var item = board.items[curr_op.body.cmd[1]]
      var column = board.columns[item.curr_column]
      historyMessage += "removed card \"" + item.name + "\" from list \"" + column.name + "\""
      if(item.removed)
        break
      item.removed = true
      column.numOfActiveItems--
      column.item_ids.splice(column.item_ids.indexOf(curr_op.body.cmd[1]),1)

      for (var i in column.item_ids) {
        var curr_item = board.items[column.item_ids[i]]
        if(curr_item.position > board.items[curr_op.body.cmd[1]].position) {
          curr_item.position--
        }
      }
      if(apply_on_ui)
        ui_remove_item(curr_op.body.cmd[1])
      break
    case Operation.ITEM_RENAME:
      var item = board.items[curr_op.body.cmd[1]]
      historyMessage += "renamed card \"" + item.name + "\" of list \""+ board.columns[item.curr_column].name +"\" to \"" + curr_op.body.cmd[2] + "\""
      item.name = curr_op.body.cmd[2]
      if(apply_on_ui)
        ui_update_item_name(curr_op.body.cmd[1], curr_op.body.cmd[2])
      break
    case Operation.ITEM_MOVE:
      var item = board.items[curr_op.body.cmd[1]]
      historyMessage += "moved card \"" + item.name + "\" of list \"" + board.columns[item.curr_column].name + "\" to list \"" + board.columns[curr_op.body.cmd[2]].name + "\""

      var old_column = board.columns[item.curr_column]
      var old_pos = item.position
      old_column.item_ids.splice(old_column.item_ids.indexOf(curr_op.body.cmd[1]), 1)
      old_column.numOfActiveItems--
      board.columns[curr_op.body.cmd[2]].numOfActiveItems++
      item.position = board.columns[curr_op.body.cmd[2]].numOfActiveItems
      board.columns[curr_op.body.cmd[2]].item_ids.push(curr_op.body.cmd[1])
      item.curr_column = curr_op.body.cmd[2].toString()
      for(var iid of old_column.item_ids) {
        let i = board.items[iid]
        if(i.position > old_pos) {
          i.position--
        }
      }
      if(apply_on_ui)
        ui_update_item_move_to_column(curr_op.body.cmd[1], curr_op.body.cmd[2], item.position)
      break
    case Operation.ITEM_SET_DESCRIPTION:
      var item = board.items[curr_op.body.cmd[1]]
      historyMessage += "changed description of card \"" + item.name + "\" of list \"" + board.columns[item.curr_column].name +"\" from \"" + item.description + "\" to \"" + curr_op.body.cmd[2] + "\""
      item.description = curr_op.body.cmd[2]
      if(apply_on_ui)
        ui_update_item_description(curr_op.body.cmd[1], curr_op.body.cmd[2])
      break
    case Operation.ITEM_POST_COMMENT:
      var item = board.items[curr_op.body.cmd[1]]
      historyMessage += "posted \"" + curr_op.body.cmd[2] + "\" on card \"" + item.name + "\" of list \"" + board.columns[item.curr_column].name + "\""
      item.comments.push([curr_op.fid, curr_op.body.cmd[2]])
      if(apply_on_ui)
        ui_item_update_chat(curr_op.body.cmd[1])
      break
    case Operation.ITEM_ASSIGN:
      var item = board.items[curr_op.body.cmd[1]]
      historyMessage += "assigned \"" + tremola.contacts[curr_op.body.cmd[2]].alias + "\" to card \"" + item.name + "\" of list \"" + board.columns[item.curr_column].name + "\""
      if(item.assignees.indexOf(curr_op.body.cmd[2]) < 0)
        item.assignees.push(curr_op.body.cmd[2])
      if(apply_on_ui)
        ui_update_item_assignees(curr_op.body.cmd[1])
      break
    case Operation.ITEM_UNASSIGN:
      var item = board.items[curr_op.body.cmd[1]]
      historyMessage += "unassigned \"" + tremola.contacts[curr_op.body.cmd[2]].alias + "\" from card \"" + item.name + "\" of list \"" + board.columns[item.curr_column].name + "\""
      if(item.assignees.indexOf(curr_op.body.cmd[2]) >= 0)
        item.assignees.splice(item.assignees.indexOf(curr_op.body.cmd[2]), 1)
      if(apply_on_ui)
        ui_update_item_assignees(curr_op.body.cmd[1])
      break
    case Operation.ITEM_COLOR:
      var item = board.items[curr_op.body.cmd[1]]
      historyMessage += "changed color of card \"" + item.name + "\" to " + curr_op.body.cmd[2]
      item.color = curr_op.body.cmd[2]
      if(apply_on_ui)
        ui_update_item_color(curr_op.body.cmd[1], curr_op.body.cmd[2])
      break
  }
  //historyMessage += ",  " + curr_op.key // debug
  board.history.push([curr_op.fid, historyMessage])
  persist()
}

function clear_board() { // removes all active columns
  var board = tremola.board[curr_board]

  for(var i in board .columns) {
    removeColumn(curr_board, i)
  }
  closeOverlay()
}

function ui_debug() {
  closeOverlay()
  document.getElementById('div:debug').style.display = 'initial'
  document.getElementById('txt:debug').value = debug_toDot()//JSON.stringify(tremola.board[curr_board])
  document.getElementById("overlay-trans").style.display = 'initial';
}

function debug_toDot() {
  var exportStr = "digraph {"
  exportStr += "  rankdir=RL;"
  exportStr += "  splines=true;"
  exportStr += "  subgraph dag {"
  exportStr += "    node[shape=Mrecord];"
  for (var p in tremola.board[curr_board].sortedOperations) {
      exportStr += '    ' + '"' + tremola.board[curr_board].sortedOperations[p] + '"' +  ' [label="' + tremola.board[curr_board].sortedOperations[p] + '\\nop=' + tremola.board[curr_board].operations[tremola.board[curr_board].sortedOperations[p]].body.cmd +'\\nr=' + tremola.board[curr_board].operations[tremola.board[curr_board].sortedOperations[p]].rank + '\\nindx=' + tremola.board[curr_board].operations[tremola.board[curr_board].sortedOperations[p]].indx +'"]'
      for (var c in tremola.board[curr_board].operations[tremola.board[curr_board].sortedOperations[p]].body.prev) {
          exportStr += '    "' + tremola.board[curr_board].sortedOperations[p] +'" -> "' + tremola.board[curr_board].operations[tremola.board[curr_board].sortedOperations[p]].body.prev[c] + '"'
    }
  }
  exportStr += "  }"
  exportStr += "  subgraph time {"
  exportStr += "    node[shape=plain];"
  exportStr += '   " t" -> " " [dir=back];'
  exportStr += "  }"
  exportStr +="}"

  return exportStr
}
