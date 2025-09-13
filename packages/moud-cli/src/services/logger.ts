import chalk from 'chalk';

class Logger {
    info(message: string) {
        console.log(chalk.blueBright(`[Moud CLI] ${message}`));
    }

    success(message: string) {
        console.log(chalk.green(`[Moud CLI] ✓ ${message}`));
    }

    warn(message: string) {
        console.log(chalk.yellow(`[Moud CLI] ❔ ${message}`));
    }

    error(message: string) {
        console.log(chalk.red(`[Moud CLI] ❌ ${message}`));
    }

    step(message: string) {
        console.log(chalk.cyan(`[Moud CLI] ${message}`));
    }
}

export const logger = new Logger();