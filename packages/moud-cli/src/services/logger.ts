import chalk from 'chalk';
import ora, { Ora } from 'ora';

class Logger {
    private activeSpinner: Ora | null = null;

    info(message: string) {
        this.stopSpinner();
        console.log(`→ ${message}`);
    }

    success(message: string) {
        this.stopSpinner();
        console.log(chalk.green(`✓ ${message}`));
    }

    warn(message: string) {
        this.stopSpinner();
        console.log(chalk.yellow(`⚠ ${message}`));
    }

    error(message: string) {
        this.stopSpinner();
        console.log(chalk.red(`✗ ${message}`));
    }

    step(message: string) {
        this.stopSpinner();
        console.log(`→ ${message}`);
    }

    spinner(message: string): Ora {
        this.stopSpinner();
        this.activeSpinner = ora(message).start();
        return this.activeSpinner;
    }

    updateSpinner(message: string) {
        if (this.activeSpinner) {
            this.activeSpinner.text = message;
        }
    }

    succeedSpinner(message: string) {
        if (this.activeSpinner) {
            this.activeSpinner.succeed(message);
            this.activeSpinner = null;
        }
    }

    failSpinner(message: string) {
        if (this.activeSpinner) {
            this.activeSpinner.fail(message);
            this.activeSpinner = null;
        }
    }

    private stopSpinner() {
        if (this.activeSpinner) {
            this.activeSpinner.stop();
            this.activeSpinner = null;
        }
    }

    progress(current: number, total: number, prefix: string = ''): string {
        const percentage = Math.round((current / total) * 100);
        const filled = Math.round((current / total) * 20);
        const bar = '█'.repeat(filled) + '░'.repeat(20 - filled);
        return `${prefix}${bar} ${percentage}%`;
    }

    showProgress(current: number, total: number, message: string) {
        const progressBar = this.progress(current, total);
        this.updateSpinner(`${message} ${progressBar}`);
    }
}

export const logger = new Logger();