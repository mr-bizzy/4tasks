"""The origins and the operations: how a change is made on the ORIGIN device, and what the server must show afterwards.

Every origin is the real path a user (or 4Dictate) takes: the 4Link door through the dev caller app, the app's own screens, the
home-screen widget, a reminder notification's Complete button, Android's share sheet, the quick-settings tile's intent.
"""
import time, re, lib

CALLER_PKG = "uk.mr_biz.fourlink.caller"
LIST = "tasks"          # the CalDAV list new tasks go to (set as the default list by reset)
LIST2 = "tasks2"

# which operations each origin can make (a door cannot edit, a share only adds, ...)
ORIGINS = {
    "door":   ["add", "tick"],
    "inapp":  ["add", "edit_title", "edit_due", "tick", "untick", "delete", "move"],
    "widget": ["add", "tick"],
    "notification": ["tick"],
    "share":  ["add"],
    "tile":   ["add"],
}
STATES = ["fg", "bg", "killed", "doze"]


class Op:
    """What one cell does. `needs` is a task that must already be on the origin (put on the server, pulled)."""

    def __init__(self, title, expect, needs=None, needs_completed=False, needs_due=None, alarm_in=None):
        self.title = title          # the task's title when the op starts (or the new task's)
        self.expect = expect        # callable(server) -> bool: the server shows the change
        self.needs = needs
        self.needs_completed = needs_completed
        self.needs_due = needs_due
        self.alarm_in = alarm_in


def make_op(server, op, stamp):
    t = f"H{stamp}"
    if op == "add":
        return Op(t, lambda s: t in s.tasks(lib_LIST()), needs=False)
    if op == "edit_title":
        new = t + "e"
        return Op(t, lambda s: new in s.tasks(lib_LIST()) and t not in s.tasks(lib_LIST()), needs=True)
    if op == "edit_due":
        return Op(t, lambda s: (s.tasks(lib_LIST()).get(t) or {}).get("due"), needs=True)
    if op == "tick":
        return Op(t, lambda s: (s.tasks(lib_LIST()).get(t) or {}).get("completed"), needs=True)
    if op == "untick":
        return Op(t, lambda s: t in s.tasks(lib_LIST()) and not s.tasks(lib_LIST())[t]["completed"], needs=True, needs_completed=True)
    if op == "delete":
        return Op(t, lambda s: t not in s.tasks(lib_LIST()), needs=True)
    if op == "move":
        return Op(t, lambda s: t in s.tasks(LIST2) and t not in s.tasks(lib_LIST()), needs=True)
    raise ValueError(op)


def lib_LIST():
    return LIST


# ----------------------------------------------------------------------------------------------------------------- helpers
def row_checkbox(dev, title, exact=True):
    n = dev.find(text=title, exact=exact)
    return (96, n.cy) if n else None


def open_list(dev):
    """The main list on screen."""
    dev.start_app()
    time.sleep(1.5)


def pull_refresh(dev):
    """The user's pull-down on the list: a sync now."""
    dev.swipe(540, 600, 540, 1500, 400)


def ensure_on_device(dev, title, timeout=120, only=True):
    """The task is in the origin's database and on its list, and (only=True) nothing else is: the state of a person's phone when
    a task has just arrived and the app is open. The server holds just that task, so pulling makes it so."""
    end = time.time() + timeout
    while time.time() < end:
        open_list(dev)
        pull_refresh(dev)
        time.sleep(4)
        t = dev.probe(timeout=6) or {}
        if title in t and (not only or set(t) == {title}):
            time.sleep(1)
            return True
        time.sleep(3)
    return False


def find_row(dev, title, tries=6):
    for _ in range(tries):
        n = dev.find(text=title, exact=True)
        if n:
            return n
        dev.swipe(540, 1700, 540, 700, 250)
        time.sleep(0.5)
    return None


def save_edit(dev):
    n = dev.tap_text(desc="Save", tries=6, wait=0.5, exact=True)
    return time.time() if n is not None else None


def type_title_and_save(dev, title):
    dev.sh("input keyevent KEYCODE_CTRL_LEFT >/dev/null 2>&1")
    time.sleep(0.4)
    dev.text(title)
    time.sleep(0.5)
    dev.key("KEYCODE_BACK")   # closes the keyboard (not the screen: the keyboard is up)
    time.sleep(0.4)
    return save_edit(dev)


def new_task_screen_ready(dev):
    for _ in range(12):
        if dev.find(desc="Save", exact=True):
            return True
        time.sleep(0.5)
    return False


# ----------------------------------------------------------------------------------------------------------------- in-app
def inapp(dev, op, o):
    open_list(dev)
    if op == "add":
        dev.tap_text(desc="Create new task", tries=6)
        new_task_screen_ready(dev)
        time.sleep(0.5)
        dev.text(o.title)
        time.sleep(0.4)
        return save_with_keyboard(dev)
    if op == "tick":
        n = find_row(dev, o.title)
        dev.tap(96, n.cy)
        return time.time()
    if op == "untick":
        c = dev.find(text="COMPLETED")
        if c:
            dev.tap_node(c)
            time.sleep(0.8)
        n = find_row(dev, o.title)
        dev.tap(96, n.cy)
        return time.time()
    n = find_row(dev, o.title)
    dev.tap_node(n)
    new_task_screen_ready(dev)
    time.sleep(0.5)
    if op == "edit_title":
        dev.tap(540, 364)                     # into the title field
        time.sleep(0.4)
        dev.key("KEYCODE_MOVE_END")
        dev.text("e")
        return save_with_keyboard(dev)
    if op == "edit_due":
        dev.tap_text("No due date", tries=4)
        time.sleep(1.2)
        dev.tap_text("Tomorrow", tries=6)
        time.sleep(0.5)
        dev.tap_text("OK", tries=6, exact=True)
        time.sleep(0.6)
        return save_edit(dev)
    if op == "delete":
        dev.tap_text(desc="Delete task", tries=4, exact=True)
        time.sleep(1.0)
        dev.tap_text("OK", tries=6, exact=True)
        return time.time()
    if op == "move":
        dev.tap_text(LIST, tries=4, exact=True)   # the list chip
        time.sleep(1.2)
        dev.tap_text(LIST2, tries=6, exact=True)
        time.sleep(0.8)
        return save_edit(dev)
    raise ValueError(op)


def save_with_keyboard(dev):
    """Closes the soft keyboard if it is up, then taps Save."""
    n = dev.find(desc="Save", exact=True)
    if not n:
        dev.key("KEYCODE_BACK")
        time.sleep(0.5)
    return save_edit(dev)


# ----------------------------------------------------------------------------------------------------------------- door (4Link)
def door_call(dev, fn, args):
    """One 4Link call through the dev caller app; returns the time the answer was logged (the change is committed then)."""
    dev.adb("logcat", "-c")
    dev.sh(f"am start -n {CALLER_PKG}/uk.mr_biz.fourlink.caller.CallActivity --es fn {fn} --es args '{args}' >/dev/null")
    end = time.time() + 40
    while time.time() < end:
        for l in dev.logcat_lines("FourLinkCaller.*RESULT"):
            t = lib.epoch_of(l)
            return t or time.time()
        time.sleep(0.3)
    return None


def door(dev, op, o):
    if op == "add":
        return door_call(dev, "tasks.add", '{"title":"%s","list":"%s"}' % (o.title, LIST))
    if op == "tick":
        return door_call(dev, "tasks.complete", '{"title":"%s"}' % o.title)
    raise ValueError(op)


# ----------------------------------------------------------------------------------------------------------------- share / tile
def share(dev, op, o):
    dev.sh(f"am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT {o.title} "
           f"-n {lib.PKG}/com.todoroo.astrid.activity.ShareLinkActivity >/dev/null")
    new_task_screen_ready(dev)
    time.sleep(0.8)
    return save_with_keyboard(dev)


def tile(dev, op, o):
    """What the quick-settings tile starts (TileService.onClick): the new-task screen. The tile cannot be clicked from adb."""
    dev.sh(f"am start -n {lib.PKG}/com.todoroo.astrid.activity.MainActivity --el open_task 0 --es create_source tile --ez remove_task true >/dev/null")
    new_task_screen_ready(dev)
    time.sleep(0.8)
    dev.text(o.title)
    time.sleep(0.4)
    return save_with_keyboard(dev)


# ----------------------------------------------------------------------------------------------------------------- widget
def widget_present(dev):
    dev.home(); time.sleep(1.2)
    return dev.find(desc="Sync now", exact=True) is not None


def ensure_widget(dev):
    """A 4Tasks widget on the home screen: the app asks the launcher to pin one (debug receiver) and the launcher's prompt is accepted."""
    if widget_present(dev):
        return True
    for _ in range(3):
        dev.start_app()
        time.sleep(3)
        dev.sh(f"am broadcast -n {lib.PKG}/org.tasks.harness.HarnessReceiver -a org.tasks.harness.ADDWIDGET >/dev/null")
        time.sleep(3)
        dev.tap_text("Add to home screen", tries=8, exact=True)
        time.sleep(2)
        if widget_present(dev):
            return True
    return False


def widget(dev, op, o):
    dev.home()
    time.sleep(1.2)
    if op == "add":
        dev.tap_text(desc="Create new task", tries=4)         # the widget's + button
        new_task_screen_ready(dev)
        time.sleep(0.6)
        dev.text(o.title)
        time.sleep(0.4)
        return save_with_keyboard(dev)
    if op == "tick":
        n = None
        for attempt in range(20):
            n = dev.find(text=o.title, exact=True)
            if n:
                break
            if attempt == 8:
                dev.tap_text(desc="Sync now", exact=True)      # the widget has not redrawn yet: its refresh button
            time.sleep(1)
        if not n:
            return None
        dev.tap(max(n.l - 62, 40), n.cy)
        return time.time()
    raise ValueError(op)


# ----------------------------------------------------------------------------------------------------------------- notification
def notification(dev, op, o):
    """The reminder notification's Complete button (the task came with a reminder due in a few seconds)."""
    dev.sh("cmd statusbar expand-notifications")
    time.sleep(1.5)
    for _ in range(10):
        if dev.tap_text("Complete", tries=1, exact=True):
            return time.time()
        time.sleep(1.0)
    return None


ORIGIN_FUNCS = {"door": door, "inapp": inapp, "widget": widget, "notification": notification, "share": share, "tile": tile}
