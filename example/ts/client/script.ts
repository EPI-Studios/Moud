const coordsDisplay = Moud.ui.createElement('text');
coordsDisplay.setText('X: 0, Y: 0, Z: 0');
coordsDisplay.setPosition(10, 10);
coordsDisplay.setSize(200, 20);
coordsDisplay.setTextColor('#FFFFFF');
coordsDisplay.setBackgroundColor('#00000080');
coordsDisplay.setPadding(5, 10, 5, 10);
coordsDisplay.showAsOverlay();

function updateCoordinates() {
    const x = Moud.camera.getX().toFixed(2);
    const y = Moud.camera.getY().toFixed(2);
    const z = Moud.camera.getZ().toFixed(2);

    coordsDisplay.setText(`X: ${x}, Y: ${y}, Z: ${z}`);
}

setInterval(updateCoordinates, 100);