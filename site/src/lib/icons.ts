// generated from @phosphor-icons/core
import icon_info from "@phosphor-icons/core/assets/fill/info-fill.svg?raw"
import icon_lightbulb from "@phosphor-icons/core/assets/fill/lightbulb-fill.svg?raw"
import icon_sealWarning from "@phosphor-icons/core/assets/fill/seal-warning-fill.svg?raw"
import icon_warning from "@phosphor-icons/core/assets/fill/warning-fill.svg?raw"
import icon_warningOctagon from "@phosphor-icons/core/assets/fill/warning-octagon-fill.svg?raw"
import icon_house from "@phosphor-icons/core/assets/fill/house-fill.svg?raw"
import icon_rocketLaunch from "@phosphor-icons/core/assets/fill/rocket-launch-fill.svg?raw"
import icon_treeStructure from "@phosphor-icons/core/assets/fill/tree-structure-fill.svg?raw"
import icon_usersThree from "@phosphor-icons/core/assets/fill/users-three-fill.svg?raw"
import icon_personSimpleRun from "@phosphor-icons/core/assets/fill/person-simple-run-fill.svg?raw"
import icon_puzzlePiece from "@phosphor-icons/core/assets/fill/puzzle-piece-fill.svg?raw"
import icon_browser from "@phosphor-icons/core/assets/fill/browser-fill.svg?raw"
import icon_chatCircleText from "@phosphor-icons/core/assets/fill/chat-circle-text-fill.svg?raw"
import icon_timer from "@phosphor-icons/core/assets/fill/timer-fill.svg?raw"
import icon_function from "@phosphor-icons/core/assets/fill/function-fill.svg?raw"
import icon_globe from "@phosphor-icons/core/assets/fill/globe-fill.svg?raw"
import icon_bug from "@phosphor-icons/core/assets/fill/bug-fill.svg?raw"
import icon_bookBookmark from "@phosphor-icons/core/assets/fill/book-bookmark-fill.svg?raw"
import icon_fileText from "@phosphor-icons/core/assets/fill/file-text-fill.svg?raw"
import icon_cube from "@phosphor-icons/core/assets/fill/cube-fill.svg?raw"
import icon_target from "@phosphor-icons/core/assets/fill/target-fill.svg?raw"
import icon_filmStrip from "@phosphor-icons/core/assets/fill/film-strip-fill.svg?raw"
import icon_layout from "@phosphor-icons/core/assets/fill/layout-fill.svg?raw"
import icon_sun from "@phosphor-icons/core/assets/fill/sun-fill.svg?raw"
import icon_sparkle from "@phosphor-icons/core/assets/fill/sparkle-fill.svg?raw"
import icon_lifebuoy from "@phosphor-icons/core/assets/fill/lifebuoy-fill.svg?raw"
import icon_gameController from "@phosphor-icons/core/assets/fill/game-controller-fill.svg?raw"
import icon_megaphone from "@phosphor-icons/core/assets/fill/megaphone-fill.svg?raw"
import icon_pushPin from "@phosphor-icons/core/assets/fill/push-pin-fill.svg?raw"
import icon_lockSimple from "@phosphor-icons/core/assets/fill/lock-simple-fill.svg?raw"
import icon_wrench from "@phosphor-icons/core/assets/fill/wrench-fill.svg?raw"
import icon_checkCircle from "@phosphor-icons/core/assets/fill/check-circle-fill.svg?raw"
import icon_discordLogo from "@phosphor-icons/core/assets/fill/discord-logo-fill.svg?raw"
import icon_githubLogo from "@phosphor-icons/core/assets/fill/github-logo-fill.svg?raw"
import icon_caretDownBold from "@phosphor-icons/core/assets/bold/caret-down-bold.svg?raw"
import icon_magnifyingGlassBold from "@phosphor-icons/core/assets/bold/magnifying-glass-bold.svg?raw"
import icon_signOutBold from "@phosphor-icons/core/assets/bold/sign-out-bold.svg?raw"
import icon_listBold from "@phosphor-icons/core/assets/bold/list-bold.svg?raw"
import icon_arrowSquareOutBold from "@phosphor-icons/core/assets/bold/arrow-square-out-bold.svg?raw"
import icon_pencilSimpleBold from "@phosphor-icons/core/assets/bold/pencil-simple-bold.svg?raw"

export const ICONS: Record<string, string> = {
  "info": icon_info,
  "lightbulb": icon_lightbulb,
  "seal-warning": icon_sealWarning,
  "warning": icon_warning,
  "warning-octagon": icon_warningOctagon,
  "house": icon_house,
  "rocket-launch": icon_rocketLaunch,
  "tree-structure": icon_treeStructure,
  "users-three": icon_usersThree,
  "person-simple-run": icon_personSimpleRun,
  "puzzle-piece": icon_puzzlePiece,
  "browser": icon_browser,
  "chat-circle-text": icon_chatCircleText,
  "timer": icon_timer,
  "function": icon_function,
  "globe": icon_globe,
  "bug": icon_bug,
  "book-bookmark": icon_bookBookmark,
  "file-text": icon_fileText,
  "cube": icon_cube,
  "target": icon_target,
  "film-strip": icon_filmStrip,
  "layout": icon_layout,
  "sun": icon_sun,
  "sparkle": icon_sparkle,
  "lifebuoy": icon_lifebuoy,
  "game-controller": icon_gameController,
  "megaphone": icon_megaphone,
  "push-pin": icon_pushPin,
  "lock-simple": icon_lockSimple,
  "wrench": icon_wrench,
  "check-circle": icon_checkCircle,
  "discord-logo": icon_discordLogo,
  "github-logo": icon_githubLogo,
  "caret-down": icon_caretDownBold,
  "magnifying-glass": icon_magnifyingGlassBold,
  "sign-out": icon_signOutBold,
  "list": icon_listBold,
  "arrow-square-out": icon_arrowSquareOutBold,
  "pencil-simple": icon_pencilSimpleBold,
}

export function iconSvg(name: string, size = "1em", cls = "") {
  const svg = ICONS[name] ?? ICONS["file-text"]
  return svg.replace("<svg", `<svg width="${size}" height="${size}"${cls ? ` class="${cls}"` : ""}`)
}
