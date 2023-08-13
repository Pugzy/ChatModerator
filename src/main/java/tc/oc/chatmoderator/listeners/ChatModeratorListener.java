package tc.oc.chatmoderator.listeners;

import com.google.common.base.Preconditions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.time.Instant;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import tc.oc.chatmoderator.ChatModeratorPlugin;
import tc.oc.chatmoderator.channels.FilteredMessageDispatcher;
import tc.oc.chatmoderator.filters.Filter;
import tc.oc.chatmoderator.listeners.managers.FilterManager;
import tc.oc.chatmoderator.listeners.managers.ZoneManager;
import tc.oc.chatmoderator.messages.FixedMessage;
import tc.oc.chatmoderator.violations.Violation;
import tc.oc.chatmoderator.zones.Zone;
import tc.oc.chatmoderator.zones.ZoneType;
import tc.oc.pgm.util.event.ChannelMessageEvent;

import java.time.Instant;

import static tc.oc.chatmoderator.ChatModeratorPlugin.MINIMUM_SCORE_NO_SEND;
import static tc.oc.chatmoderator.ChatModeratorPlugin.PARTIALLY_OFFENSIVE_RATIO;

/**
 * Listener for chat-related events.
 */
public final class ChatModeratorListener implements Listener {
    private final ChatModeratorPlugin plugin;

    private final FilterManager filterManager;
    private final ZoneManager zoneManager;
    private final FilteredMessageDispatcher filteredDispatcher;

    public ChatModeratorListener(final ChatModeratorPlugin plugin) {
        Preconditions.checkArgument(Preconditions.checkNotNull(plugin, "Plugin").isEnabled(), "Plugin not loaded.");
        this.plugin = plugin;

        this.filterManager = new FilterManager(this.plugin);
        this.zoneManager = new ZoneManager(this.plugin);
        this.filteredDispatcher = new FilteredMessageDispatcher(
                plugin.getConfig().getString("channels.global.format"),
                new Permission("channels.global", PermissionDefault.TRUE),
                MINIMUM_SCORE_NO_SEND,
                PARTIALLY_OFFENSIVE_RATIO
        );
    }

    /**
     * Gets the filter manager.
     *
     * @return The filter manager.
     */
    public FilterManager getFilterManager() {
        return this.filterManager;
    }

    /**
     * Gets the zone manager.
     *
     * @return The zone manager.
     */
    public ZoneManager getZoneManager() {
        return this.zoneManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.plugin.getPlayerManager().removeViolationSetFor(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        this.plugin.getPlayerManager().removeViolationSetFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChatEvent(AsyncPlayerChatEvent event) {
        Zone channelZone = this.getZoneManager().getZone(ZoneType.CHANNEL);

        if (!channelZone.isEnabled() || event.getPlayer() == null) {
            return;
        }

        String message = Preconditions.checkNotNull(event, "Event").getMessage();
        Player player = event.getPlayer();

        FixedMessage fixedMessage = new FixedMessage(message, Instant.now());
        fixedMessage.setFixed(new String(fixedMessage.getOriginal()));

        for (Filter filter : this.getFilterManager().getFiltersForZone(channelZone)) {
            if (fixedMessage.getFixed() == null || fixedMessage.getFixed().equals("")) {
                break;
            }

            filter.filter(fixedMessage, player, ZoneType.CHANNEL, event);
        }
        this.plugin.getPlayerManager().getViolationSet(player).setLastMessage(fixedMessage);

        event.setMessage(fixedMessage.getOriginal());

        for (Violation v : plugin.getPlayerManager().getViolationSet(player).getViolationsForTime(fixedMessage.getTimeSent())) {
            if (v.isCancelled()) {
                event.setMessage(null);
                event.setCancelled(true);
                break;
            }

            if (v.isFixed()) {
                event.setMessage(fixedMessage.getFixed());
            }
        }

        if (event.getMessage() == null) {
            event.setCancelled(true);
        }

        this.filteredDispatcher.onPlayerChat(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignEdit(SignChangeEvent event) {
        Zone signZone = this.getZoneManager().getZone(ZoneType.SIGN);

        if(!(signZone.isEnabled()) || event.getPlayer() == null)
            return;

        Player player = event.getPlayer();
        Instant signCreateInstant;

        for (int i = 0; i < event.getLines().length; i++) {
            signCreateInstant = Instant.now();

            FixedMessage message = new FixedMessage(event.getLine(i), signCreateInstant);
            message.setFixed(message.getOriginal());

            if (event.getLine(i).equals("") || event.getLine(i) == null) {
                continue;
            }

            for (Filter filter : this.getFilterManager().getFiltersForZone(signZone)) {
                filter.filter(message, player, ZoneType.SIGN, event);
            }

            event.setLine(i, message.getOriginal());

            for (Violation v : plugin.getPlayerManager().getViolationSet(player).getViolationsForTime(signCreateInstant)) {
                if (v.isCancelled()) {
                    event.setLine(i, "");
                    event.setCancelled(true);
                    break;
                }

                if (v.isFixed()) {
                    event.setLine(i, message.getFixed());
                }
            }

        }
    }
}
