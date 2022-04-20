# Use Tremola app to add another activity

Little guide to use Tremola to add a functionality in GUI.

This is a step-by-step guide to add your own element into the app. It can add a functionality for the app, but it can
also let you use the architecture of the app for a completely different function.

Don't worry if you are new to HTML, CSS and javascript, this tutorial assumes no previous knowledge. Nevertheless, it
might be useful to know the basics. I recommend
<a href="https://developer.mozilla.org/en-US/docs/Learn/Getting_started_with_the_web">this web development</a> tutorial.
The whole tutorial is long, but the first part describes the basics to give you a first idea of each language.

If not otherwise specified, the files are under the folder app/src/main/assets/web

### Note

This tutorial let you modify mostly instances (like arrays and lists), but make sure to look in the code how these are
used, to get a good understanding of how the code works.

## Installing the software

To run this app, you need to install the android SDK. You can either
install [Android studio](https://developer.android.com/studio#downloads) or use Intellij directly, but you will need to
install the android SDK.

To run the application, you can either install a virtual device with the SDK manager or use a physical android phone
(you need to [enable developer options and USB debugging](https://developer.android.com/studio/debug/dev-options#enable)
for that).

It works with Intellij Community too (the free version), but javascript plugins are only for the Ultimate edition (you
can have it for free with your university account).

## Step 1: add HTML element

Go to the file tremola.html. There is a `<div>` element inside the `<body>` element. Here, you can add a new element.
Describe your layout and give it an ID (we will call it `'game:ui'`). The `'core'` div is a good template if you don't
know what to write, but you have to add "display: none;"
in the style, not to mess with the other tags:
`style="display: none;overflow:scroll;[...]"`.

Leave the rest empty for the moment, as we will first focus on how to access it, not what it contains or does.

### Add your HTML element to the display list

We will now go to the javascript file tremola_ui.js. The first thing to do is to let your code know that there is a new
HTML element. The variable `display_or_not`is an array of all HTML elements. Every time the app switch from one scenario
to another, it will run through this list and make sure each element is displayed (or not) as it is supposed to be. Just
add your element (`game:ui`) there.

We now want to enable a way to access our game. By "access", we mean a way to launch some code from the GUI for our game
or functionality.

## Step 2: add your own scenario

First, we need to create a scenario. It is the base element of the implementation, describing the state of the GUI. In
the list `scenarioDisplay`, add a new element, for example
`'game': ['game:ui'],`.

Here, `game` is the name of the scenario and `game:ui` is the HTML element we just created and want to have on the
screen. You can have other elements too; for example `div:back`, which is the button in the top left corner to go back (
but using your phone's `return` button will have the same result). Other elements include `core`, `div:footer`
and `plus` (see `display_or_not` for a complete list and tremola.html for more information).

Furthermore, we want to add it to the `main_scenarios` list. We will explain later why.

## Step 3: add access to your element

We now want to run our scenario, and we have different possibilities for that; we will describe two of them.
<br>
Note that more than one access can be enabled, and it is easy to later add or delete them.

### With a menu item

A very simple way to add an access is with a menu item. In the tremola_ui.js file, there is an object named
`scenarioMenu` which is an array of arrays with menu items, one array for each scenario. Each menu item is an element
with two strings. The first one is the text that will be shown on screen, the second one is the name of the function
that will be called when clicking on this item. In the 'chats' menu, add an element `['Launch my game', 'add_game'],` (
pay attention to the commas).

You can now test it. You should have a new menu item, but nothing happens when you click it.

### With a button in the footer

Another way is to use the footer. For that, go back to the tremola.html file and find the `div:footer` element. You have
there a list of 3 `<td>` elements. Add a new one and give it the id `'btn:game'` (if you named your scenario `game`).
You then need to adapt the width of the elements. Tip for the background: you can use any image from the project, for
example `style="background-image: url('img/checked.svg');"`. You can also add a new image in the ./img/ file, or write
some text after the button tag.<br>
The `onclick` element gives us the name of the method that will be called when the user clicks on the button.
Write `add_game();` here (we will create that function in the next step).

But before that, we need to let the implementation know that there's a new button element. In the tremola_ui.js file,
there is an element called `buttonList`. You can add the name of your button there.

### Add your function

We will now make something happen. In the last step, you chose a name for a function, and we will now create it. In
tremola_ui.js, add `function add_game() {}`. Call the method `setScenario('game');` from here, it will switch to your
scenario.

Calling the function `closeOverlay();` will close the menu list you opened. The function `launch_snakbar("Some text");`
will display the text you give it, which comes in very handy for debugging.

## Step 4: Connect with the backend

We have now seen how to add an element in the GUI, but for using the properties of Secure ScuttleButt (SSB), we need to
contact the backend.

### Send a message from the frontend

Sending a message from the GUI is very straight forward. Indeed, the method `backend(string)` in tremola.js does most of
the job.

Start your string with an identifier, then any argument you want to send:<br>
`backend('game:ui hello!');`. <br>
Write it in `add_game()`.

### Receive a message in the backend

If you run your code now and look at the log, you will see a debug info like
`/nz.scuttlebutt.tremola D/FrontendRequest: game:ui hello!`. This comes from the method `onFrontendRequest()` in the
file src/main/java/nz/scuttlebutt/tremola/WebAppInterface.kt.

In the `when` loop (if you don't know kotlin, think of it as an alternative of `switch`), you can add an element that
handle your specific case.

Tip: use the Logger for debuging. `Log.d` is for debug, `Log.e` for errors (appears in red, which might be useful), etc.

### Send a reply from backend

Sending a reply is even easier. Simply calls the method `WebAppInterface::eval` with the name of the method you want to
run. However, we need to be very careful with the quotes. Read the doc for this method and look at how it is called by
other part of the code.

We will call `receive_from_backend('Reply to ${args[1]}: Hi!')`. But of course, you could do anything from here,
including adding a new class, storing it in the backup, or sending a message over the internet.

### Receive a reply in the frontend

All we have to do now is to write a method in the frontend part. We will write it in tremola_ui.js and just print the
received string on the console.

## Step 5: An example

As an example, we added at the bottom of the HTML file an element with id='game-ui'. Comment it out and try to
understand what elements it describes.

As the id is game:ui, you need to make sure you comment out the other element you just created. Also, make sure it is
correctly described in `display_or_not` and `scenario_display`.

The main element is a `<table>` element with two rows. The most important things in the first element are its
id (`game:counter`), which lets us access it easily, and the `1` in the content, which is the only thing that
effectively appears on screen. The second element is a button that has an attribute `onclick` with value
= `increment();`. As the program will try to run this function, you have to implement it (in tremola_ui.js for example).

A few tips: `document.getElementById()` is the method you need. You can then extract the `innerText` from the element.
Be aware that you receive a string, need to parse it to an int and recast it to a string after incrementing.

## Step 6: add other functionalities

We now have a custom scenario where we can do anything we want. We will now see a few additional settings that can be
added to your functionality.

### Add a menu

A menu is often useful as it is easy to implement and intuitive for the user. We already explained how to add a menu
item earlier in the first possibility to launch the scenario, and what we will do here is very similar. Go back to
the `scenarioMenu` and add a new element (a whole element like `chat`, not like `Settings` as before). You need to name
it like your scenario (`'game'`). Then, add the different items you want. You can add some that exist in the other
scenario menus, or add a custom element that calls a new function.

If you add `['Settings', 'menu_settings']`, you probably want to come back to your `game` scenario when coming back.
This is why we added our scenario to the array `main_scenarios`: it will set `game` as previous (`prev_scenario`).
Otherwise, we would return to the one before.

### Add code in a separate file

If you add a lot of code, you might want to write it in a new file. For that, add your file in the `web/` folder
(in Intellij: right-click on the folder, new -> Javascript file (or other)) and write your code there. Then, in
tremola.html, add a `<script>` element inside of `<head>` with your file name. And that's it!

### Some tips

If you want to debug faster, you can make the app launch the scenario you want from the beginning.
In `tremola::b2f_initialize`, change the last line to call `setScenario('game')`. You can also add a call to any
function to bypass navigating through the GUI to see your changes.
