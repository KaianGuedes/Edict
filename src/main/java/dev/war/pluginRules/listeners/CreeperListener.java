package dev.war.pluginRules;

import dev.war.physarum.managers.RuleManager;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public class CreeperListener implements Listener {

    private final RuleManager ruleManager;

    public CreeperListener(RuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    @EventHandler
    public void onCreeperExplode(EntityExplodeEvent event) {
        // Verifica se a entidade que causou a explosão é um Creeper
        if (event.getEntity() instanceof Creeper) {
            // Consulta o nosso sistema de regras
            if (!ruleManager.getRule("creeper_block_damage")) {
                // Limpa a lista de blocos afetados. A explosão (dano/som) acontece, mas o cenário fica intacto.
                event.blockList().clear();
            }
        }
    }
}