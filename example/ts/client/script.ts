const container = Moud.ui.createContainer();
container.setPosition(10, 10);
container.setSize(200, 50);
container.setBackgroundColor("#00000000");

const letters = ["M", "o", "u", "d", " ", "A", "l", "p", "h", "a"];
const letterElements = [];
const letterStates = [];

letters.forEach((letter, index) => {
    const letterElement = Moud.ui.createText(letter);
    const baseX = index * 18;
    letterElement.setPosition(baseX, 0);
    letterElement.setSize(18, 30);
    letterElement.setTextColor("#FFFFFF");
    letterElement.setBackgroundColor("#00000000");

    letterElements.push(letterElement);
    container.appendChild(letterElement);

    letterStates.push({
        baseX: baseX,
        baseY: 0,
        currentX: baseX,
        currentY: 0,
        phase: Math.random() * Math.PI * 2,
        amplitude: 2,
        frequency: 2
    });
});

container.showAsOverlay();

function updateAnimation(deltaTime) {
    letterStates.forEach((state, index) => {
        const time = deltaTime + state.phase;
        const wiggleX = Math.sin(time * state.frequency) * state.amplitude;
        const wiggleY = Math.cos(time * state.frequency * 1.3) * state.amplitude * 0.6;

        const targetX = state.baseX + wiggleX;
        const targetY = state.baseY + wiggleY;

        state.currentX += (targetX - state.currentX) * 0.2;
        state.currentY += (targetY - state.currentY) * 0.2;

        letterElements[index].setPosition(
            Math.round(state.currentX),
            Math.round(state.currentY)
        );
    });

    requestAnimationFrame(updateAnimation);
}

requestAnimationFrame(updateAnimation);