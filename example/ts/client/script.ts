console.log("Testing new UI system...");

var ui = Moud.ui;
var cursor = Moud.cursor;

cursor.show();

var container = ui.createContainer();
container.setPosition(50, 50);
container.setSize(400, 300);
container.setBackgroundColor("#2C2C2C");
container.setBorder(2, "#4A4A4A");
container.setFlexDirection("column");
container.setGap(10);
container.setPadding(20, 20, 20, 20);

var title = ui.createText("UI System Test");
title.setTextColor("#FFFFFF");
title.setTextAlign("center");
title.setSize(360, 30);

var button = ui.createButton("Click Me");
button.setSize(150, 30);
button.setBackgroundColor("#4CAF50");
button.setTextColor("#FFFFFF");
button.onClick(function() {
    console.log("Button clicked!");
    input.setValue("Button was clicked at " + new Date().toLocaleTimeString());
});

var input = ui.createInput("Type something here...");
input.setSize(300, 25);
input.onChange(function(element, newValue, oldValue) {
    console.log("Input changed:", newValue);
});

container.appendChild(title);
container.appendChild(button);
container.appendChild(input);

container.showAsOverlay();

console.log("UI test setup complete. Screen size:", ui.getScreenWidth(), "x", ui.getScreenHeight());

setTimeout(function() {
    console.log("Testing position change...");
    container.setPosition(100, 80);
}, 3000);