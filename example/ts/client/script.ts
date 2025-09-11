

moudAPI.console.log("Client Script: Loading Command Bar UI Test...");

function createCommandBarUI() {
    const commandContainer = moudAPI.ui.createContainer();
    commandContainer
        .setSize(400, 30)
        .setBackgroundColor("#00000088")
        .setFlexDirection("row")
        .setAlignItems("center")
        .setGap(5)
        .setPadding(0, 5, 0, 5);

    const screenWidth = moudAPI.ui.getScreenWidth();
    const screenHeight = moudAPI.ui.getScreenHeight();

    commandContainer.setPosition(
        (screenWidth / 2) - (commandContainer.getWidth() / 2),
        screenHeight - commandContainer.getHeight() - 10
    );

    const icon = moudAPI.ui.createImage("minecraft:textures/item/writable_book.png");
    icon.setSize(20, 20);

    const commandInput = moudAPI.ui.createInput("Appuyez sur F8 pour écrire...");
    commandInput
        .setSize(300, 24)
        .setBackgroundColor("#222222")
        .setTextColor("#E0E0E0")
        .setBorder(1, "#555555");

    const sendButton = moudAPI.ui.createButton("Envoyer");
    sendButton.setSize(50, 24).setBackgroundColor("#4A90E2").setTextColor("#FFFFFF");

    commandContainer.appendChild(icon);
    commandContainer.appendChild(commandInput);
    commandContainer.appendChild(sendButton);


    commandInput.onFocus(() => {
        moudAPI.console.log("Champ de commande focus.");
        commandInput.setBorder(1, "#4A90E2");
    });

    commandInput.onBlur(() => {
        moudAPI.console.log("Champ de commande dé-focus.");
        commandInput.setBorder(1, "#555555");

        if (moudAPI.cursor.isVisible()) {
            moudAPI.cursor.hide();
        }
    });

    const handleSendCommand = () => {
        const commandText = commandInput.getValue();
        if (commandText.trim() === "") return;

        moudAPI.console.log(`Envoi de la commande : "${commandText}"`);
        moudAPI.network.sendToServer("ui:command_sent", { command: commandText });
        commandInput.setValue("");

        commandInput.triggerBlur();
    };

    sendButton.onClick(handleSendCommand);

    commandContainer.showAsOverlay();
    moudAPI.console.log("L'interface de la barre de commande est visible. Appuyez sur F8 pour basculer le curseur.");
}

createCommandBarUI();