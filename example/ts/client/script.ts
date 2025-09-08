const versionText = Moud.ui.createElement('div')
    .setPosition(10, 10)
    .setSize(150, 20)
    .setBackgroundColor('#00000000')
    .setTextColor('#FFFFFF')
    .setText('Moud v0.1.0-alpha')
    .showAsOverlay();

Moud.console.log('Version display loaded');