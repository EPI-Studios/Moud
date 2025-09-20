api.getWorld()
    .setFlatGenerator()
    .setSpawn(0, 64, 0);

api.on('player.chat', (event) => {
    const player = event.getPlayer();
    const message = event.getMessage();

    if (message.startsWith('!shake')) {
        const intensity = 0.5;
        const durationMs = 2000;

        console.log(`Starting shake effect - intensity: ${intensity}, duration: ${durationMs}ms`);

        player.camera.shake(intensity, durationMs);
    }

    if (message.startsWith('!camera')) {
        event.cancel();

        const args = message.split(' ');
        const command = args[1];

        if (command === 'lock') {
            const x = args[2] ? parseFloat(args[2]) : player.getPosition().x;
            const y = args[3] ? parseFloat(args[3]) : player.getPosition().y + 10;
            const z = args[4] ? parseFloat(args[4]) : player.getPosition().z;
            const yaw = args[5] ? parseFloat(args[5]) : 0;
            const pitch = args[6] ? parseFloat(args[6]) : -45;

            player.getCamera().lock(new Vector3(x, y, z), {
                yaw: yaw,
                pitch: pitch,
                smooth: true,
                speed: 2.0,
                disableViewBobbing: true,
                disableHandMovement: true
            });

            player.sendMessage(`Camera locked at ${x}, ${y}, ${z}`);
        }

        if (command === 'release') {
            player.getCamera().release();
            player.sendMessage('Camera released');
        }

        if (command === 'spin') {
            if (!player.getCamera().isLocked()) {
                player.sendMessage('Lock camera first with !camera lock');
                return;
            }

            const speed = args[2] ? parseFloat(args[2]) : 1.0;
            let currentYaw = 0;

            const spinInterval = setInterval(() => {
                currentYaw += speed;
                if (currentYaw >= 360) currentYaw = 0;

                player.getCamera().setRotation({
                    yaw: currentYaw,
                    pitch: -45
                });
            }, 50);

            setTimeout(() => {
                clearInterval(spinInterval);
                player.sendMessage('Spin completed');
            }, 5000);

            player.sendMessage(`Spinning camera at speed ${speed}`);
        }

        if (command === 'orbit') {
            const centerX = args[2] ? parseFloat(args[2]) : player.getPosition().x;
            const centerY = args[3] ? parseFloat(args[3]) : player.getPosition().y + 5;
            const centerZ = args[4] ? parseFloat(args[4]) : player.getPosition().z;
            const radius = args[5] ? parseFloat(args[5]) : 10;

            let angle = 0;

            const orbitInterval = setInterval(() => {
                const x = centerX + Math.cos(angle) * radius;
                const z = centerZ + Math.sin(angle) * radius;

                player.getCamera().setPosition(new Vector3(x, centerY, z));
                player.getCamera().lookAt(new Vector3(centerX, centerY - 2, centerZ));

                angle += 0.05;

                if (angle >= Math.PI * 2) {
                    clearInterval(orbitInterval);
                    player.sendMessage('Orbit completed');
                }
            }, 50);

            player.sendMessage(`Orbiting around ${centerX}, ${centerY}, ${centerZ}`);
        }

        if (command === 'smooth') {
            const targetX = args[2] ? parseFloat(args[2]) : player.getPosition().x + 10;
            const targetY = args[3] ? parseFloat(args[3]) : player.getPosition().y + 5;
            const targetZ = args[4] ? parseFloat(args[4]) : player.getPosition().z + 10;
            const duration = args[5] ? parseInt(args[5]) : 3000;

            if (!player.getCamera().isLocked()) {
                player.getCamera().lock(player.getPosition());
            }

            player.getCamera().smoothTransitionTo(
                new Vector3(targetX, targetY, targetZ),
                { yaw: 90, pitch: -30 },
                duration
            );

            player.sendMessage(`Smooth transition to ${targetX}, ${targetY}, ${targetZ} over ${duration}ms`);
        }

        if (command === 'tilt') {
            const roll = args[2] ? parseFloat(args[2]) : 15;

            if (!player.getCamera().isLocked()) {
                player.getCamera().lock(player.getPosition());
            }

            player.getCamera().setRotation({
                yaw: player.getYaw(),
                pitch: player.getPitch(),
                roll: roll
            });

            player.sendMessage(`Camera tilted by ${roll} degrees`);
        }

        if (command === 'help') {
            player.sendMessage('Camera commands:');
            player.sendMessage('!camera lock [x] [y] [z] [yaw] [pitch] - Lock camera');
            player.sendMessage('!camera release - Release camera');
            player.sendMessage('!camera spin [speed] - Spin camera around');
            player.sendMessage('!camera orbit [x] [y] [z] [radius] - Orbit around point');
            player.sendMessage('!camera smooth [x] [y] [z] [duration] - Smooth transition');
            player.sendMessage('!camera tilt [roll] - Tilt camera');
        }
    }

    if (message.startsWith('!cursor')) {
        event.cancel();

        const args = message.split(' ');
        const command = args[1];

        if (command === 'show') {
            player.getCursor().setVisible(true);
            player.getCursor().setVisibleToAll();
            player.sendMessage('Cursor enabled for all players');
        }

        if (command === 'hide') {
            player.getCursor().setVisible(false);
            player.sendMessage('Cursor hidden');
        }

        if (command === 'color') {
            const r = args[2] ? parseFloat(args[2]) : 1.0;
            const g = args[3] ? parseFloat(args[3]) : 0.0;
            const b = args[4] ? parseFloat(args[4]) : 0.0;

            player.getCursor().setColor({ r: r, g: g, b: b });
            player.sendMessage(`Cursor color set to RGB(${r}, ${g}, ${b})`);
        }

        if (command === 'scale') {
            const scale = args[2] ? parseFloat(args[2]) : 2.0;
            player.getCursor().setScale(scale);
            player.sendMessage(`Cursor scale set to ${scale}`);
        }

        if (command === 'texture') {
            const texture = args[2] || 'minecraft:textures/gui/icons.png';
            player.getCursor().setTexture(texture);
            player.sendMessage(`Cursor texture set to ${texture}`);
        }

        if (command === 'info') {
            player.getCursor().update();
            const pos = player.getCursor().getWorldPosition();
            const hit = player.getCursor().isHit();
            const block = player.getCursor().getHitBlock();
            const distance = player.getCursor().getDistance();

            player.sendMessage(`Cursor at: ${pos.x.toFixed(2)}, ${pos.y.toFixed(2)}, ${pos.z.toFixed(2)}`);
            player.sendMessage(`Hit: ${hit}, Block: ${block}, Distance: ${distance.toFixed(2)}`);
        }

        if (command === 'help') {
            player.sendMessage('Cursor commands:');
            player.sendMessage('!cursor show - Show cursor to all players');
            player.sendMessage('!cursor hide - Hide cursor');
            player.sendMessage('!cursor color [r] [g] [b] - Set cursor color');
            player.sendMessage('!cursor scale [scale] - Set cursor scale');
            player.sendMessage('!cursor texture [texture] - Set cursor texture');
            player.sendMessage('!cursor info - Show cursor information');
        }
    }

    if (message.startsWith('!ui')) {
        event.cancel();

        const args = message.split(' ');
        const command = args[1];

        if (command === 'hide') {
            const element = args[2];

            if (element === 'all') {
                player.getUi().hide();
                player.sendMessage('All UI elements hidden');
            } else if (element === 'hotbar') {
                player.getUi().hideHotbar();
                player.sendMessage('Hotbar hidden');
            } else if (element === 'health') {
                player.getUi().hideHealth();
                player.sendMessage('Health hidden');
            } else if (element === 'food') {
                player.getUi().hideFood();
                player.sendMessage('Food hidden');
            } else if (element === 'crosshair') {
                player.getUi().hideCrosshair();
                player.sendMessage('Crosshair hidden');
            } else if (element === 'chat') {
                player.getUi().hideChat();
                player.sendMessage('Chat hidden');
            } else {
                player.getUi().hide({
                    hotbar: args.includes('hotbar'),
                    health: args.includes('health'),
                    food: args.includes('food'),
                    crosshair: args.includes('crosshair'),
                    chat: args.includes('chat'),
                    hand: args.includes('hand'),
                    experience: args.includes('experience')
                });
                player.sendMessage('Selected UI elements hidden');
            }
        }

        if (command === 'show') {
            const element = args[2];

            if (element === 'all') {
                player.getUi().show();
                player.sendMessage('All UI elements shown');
            } else if (element === 'hotbar') {
                player.getUi().showHotbar();
                player.sendMessage('Hotbar shown');
            } else if (element === 'health') {
                player.getUi().showHealth();
                player.sendMessage('Health shown');
            } else if (element === 'food') {
                player.getUi().showFood();
                player.sendMessage('Food shown');
            } else if (element === 'crosshair') {
                player.getUi().showCrosshair();
                player.sendMessage('Crosshair shown');
            } else if (element === 'chat') {
                player.getUi().showChat();
                player.sendMessage('Chat shown');
            } else {
                player.getUi().show({
                    hotbar: args.includes('hotbar'),
                    health: args.includes('health'),
                    food: args.includes('food'),
                    crosshair: args.includes('crosshair'),
                    chat: args.includes('chat'),
                    hand: args.includes('hand'),
                    experience: args.includes('experience')
                });
                player.sendMessage('Selected UI elements shown');
            }
        }

        if (command === 'help') {
            player.sendMessage('UI commands:');
            player.sendMessage('!ui hide [all|hotbar|health|food|crosshair|chat|hand|experience]');
            player.sendMessage('!ui show [all|hotbar|health|food|crosshair|chat|hand|experience]');
            player.sendMessage('You can specify multiple elements: !ui hide hotbar health food');
        }
    }

    if (message === '!help') {
        event.cancel();
        player.sendMessage('Available commands:');
        player.sendMessage('!shake [intensity] [duration] - Shake camera effect');
        player.sendMessage('!camera help - Camera control commands');
        player.sendMessage('!cursor help - Cursor control commands');
        player.sendMessage('!ui help - UI control commands');
    }
});

api.on('player.join', (player) => {
    player.sendMessage('Welcome! Type !help for available commands');
    player.getCursor().setVisibleToAll();
});

setInterval(() => {
    api.getServer().getPlayers().forEach(player => {
        player.getCursor().update();
    });
}, 100);