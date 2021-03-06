/*
 * Copyright (c) 2018, Jos <Malevolentdev@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.statusbars;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCDefinition;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.itemstats.ItemStatPlugin;
import net.runelite.client.plugins.statusbars.config.BarMode;
import net.runelite.client.plugins.statusbars.renderer.BarRenderer;
import net.runelite.client.plugins.statusbars.renderer.EnergyRenderer;
import net.runelite.client.plugins.statusbars.renderer.HitPointsRenderer;
import net.runelite.client.plugins.statusbars.renderer.PrayerRenderer;
import net.runelite.client.plugins.statusbars.renderer.SpecialAttackRenderer;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
	name = "Status Bars",
	enabledByDefault = false,
	description = "Draws status bars next to players inventory showing currentValue and restore amounts",
	type = PluginType.UTILITY
)
@PluginDependency(ItemStatPlugin.class)
public class StatusBarsPlugin extends Plugin
{
	@Inject
	private StatusBarsOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private HitPointsRenderer hitPointsRenderer;

	@Inject
	private PrayerRenderer prayerRenderer;

	@Inject
	private EnergyRenderer energyRenderer;

	@Inject
	private SpecialAttackRenderer specialAttackRenderer;

	@Getter(AccessLevel.PACKAGE)
	private final Map<BarMode, BarRenderer> barRenderers = new EnumMap<>(BarMode.class);

	@Inject
	private Client client;

	@Inject
	private StatusBarsConfig config;

	@Getter(AccessLevel.PACKAGE)
	private Instant lastCombatAction;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		barRenderers.put(BarMode.DISABLED, null);
		barRenderers.put(BarMode.HITPOINTS, hitPointsRenderer);
		barRenderers.put(BarMode.PRAYER, prayerRenderer);
		barRenderers.put(BarMode.RUN_ENERGY, energyRenderer);
		barRenderers.put(BarMode.SPECIAL_ATTACK, specialAttackRenderer);
	}

	private void updateLastCombatAction()
	{
		this.lastCombatAction = Instant.now();
	}

	@Subscribe
	private void onGameTick(GameTick gameTick)
	{
		if (!config.toggleRestorationBars())
		{
			overlayManager.add(overlay);
		}
		else
		{
			hideStatusBar();
		}
	}

	private void hideStatusBar()
	{
		final Actor interacting = client.getLocalPlayer().getInteracting();
		final boolean isNpc = interacting instanceof NPC;
		final int combatTimeout = config.hideStatusBarDelay();

		if (isNpc)
		{
			final NPC npc = (NPC) interacting;
			final NPCDefinition npcComposition = npc.getDefinition();
			final List<String> npcMenuActions = Arrays.asList(npcComposition.getActions());
			if (npcMenuActions.contains("Attack") && config.toggleRestorationBars())
			{
				updateLastCombatAction();
				overlayManager.add(overlay);
			}
		}
		else if (lastCombatAction == null || Duration.between(getLastCombatAction(), Instant.now()).getSeconds() > combatTimeout)
		{
			overlayManager.remove(overlay);
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		barRenderers.clear();
	}

	@Provides
	StatusBarsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(StatusBarsConfig.class);
	}
}