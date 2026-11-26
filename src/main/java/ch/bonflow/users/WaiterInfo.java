package ch.bonflow.users;

/** What the app needs to know about a waiter: who to show in the login list and who is logged in. */
public record WaiterInfo(long id, String displayName) {

    static WaiterInfo of(AppUser user) {
        return new WaiterInfo(user.getId(), user.getDisplayName());
    }
}
