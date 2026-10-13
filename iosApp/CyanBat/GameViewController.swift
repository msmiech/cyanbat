import CyanBatGame
import UIKit

/// Holds the game's Compose view controller - the menu and every run, built in Kotlin (the :ios
/// module) - and says how the system's own furniture behaves over it. Compose's controller does
/// not let these be overridden, so this one owns it and answers for it.
final class GameViewController: UIViewController {
    private let game = MainViewControllerKt.MainViewController()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        addChild(game)
        game.view.frame = view.bounds
        game.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(game.view)
        game.didMove(toParent: self)
    }

    /// The game is played sideways, edge to edge; the clock and battery would sit over the HUD.
    override var prefersStatusBarHidden: Bool { true }

    /// The home indicator fades out once the player stops touching its edge.
    override var prefersHomeIndicatorAutoHidden: Bool { true }

    /// A finger steering the bat strays onto the screen's edges, and a swipe there must not throw
    /// the player out of a run: the first swipe from an edge only shows the system's grabber, and
    /// a second one acts.
    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge { .all }
}
