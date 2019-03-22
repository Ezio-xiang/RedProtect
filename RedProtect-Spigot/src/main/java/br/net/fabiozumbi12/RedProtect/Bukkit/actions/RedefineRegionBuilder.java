/*
 Copyright @FabioZumbi12

 This class is provided 'as-is', without any express or implied warranty. In no event will the authors be held liable for any
  damages arising from the use of this class.

 Permission is granted to anyone to use this class for any purpose, including commercial plugins, and to alter it and
 redistribute it freely, subject to the following restrictions:
 1 - The origin of this class must not be misrepresented; you must not claim that you wrote the original software. If you
 use this class in other plugins, an acknowledgment in the plugin documentation would be appreciated but is not required.
 2 - Altered source versions must be plainly marked as such, and must not be misrepresented as being the original class.
 3 - This notice may not be removed or altered from any source distribution.

 Esta classe é fornecida "como está", sem qualquer garantia expressa ou implícita. Em nenhum caso os autores serão
 responsabilizados por quaisquer danos decorrentes do uso desta classe.

 É concedida permissão a qualquer pessoa para usar esta classe para qualquer finalidade, incluindo plugins pagos, e para
 alterá-lo e redistribuí-lo livremente, sujeito às seguintes restrições:
 1 - A origem desta classe não deve ser deturpada; você não deve afirmar que escreveu a classe original. Se você usar esta
  classe em um plugin, uma confirmação de autoria na documentação do plugin será apreciada, mas não é necessária.
 2 - Versões de origem alteradas devem ser claramente marcadas como tal e não devem ser deturpadas como sendo a
 classe original.
 3 - Este aviso não pode ser removido ou alterado de qualquer distribuição de origem.
 */

package br.net.fabiozumbi12.RedProtect.Bukkit.actions;

import br.net.fabiozumbi12.RedProtect.Bukkit.RPUtil;
import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;
import br.net.fabiozumbi12.RedProtect.Bukkit.Region;
import br.net.fabiozumbi12.RedProtect.Bukkit.RegionBuilder;
import br.net.fabiozumbi12.RedProtect.Bukkit.config.RPConfig;
import br.net.fabiozumbi12.RedProtect.Bukkit.config.RPLang;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class RedefineRegionBuilder extends RegionBuilder {

    @SuppressWarnings("deprecation")
    public RedefineRegionBuilder(Player p, Region old, Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) {
            this.setError(p, RPLang.get("regionbuilder.selection.notset"));
            return;
        }

        //check if distance allowed
        if (loc1.getWorld().equals(loc2.getWorld()) && new Region(null, loc1, loc2, null).getArea() > RPConfig.getInt("region-settings.define-max-distance") && !RedProtect.get().ph.hasPerm(p, "redprotect.bypass.define-max-distance")) {
            double dist = new Region(null, loc1, loc2, null).getArea();
            RPLang.sendMessage(p, String.format(RPLang.get("regionbuilder.selection.maxdefine"), RPConfig.getInt("region-settings.define-max-distance"), dist));
            return;
        }

        World w = p.getWorld();

        int miny = loc1.getBlockY();
        int maxy = loc2.getBlockY();
        if (RPConfig.getBool("region-settings.autoexpandvert-ondefine")) {
            miny = 0;
            maxy = w.getMaxHeight();
            if (RPConfig.getInt("region-settings.claim.miny") != -1)
                miny = RPConfig.getInt("region-settings.claim.miny");
            if (RPConfig.getInt("region-settings.claim.maxy") != -1)
                maxy = RPConfig.getInt("region-settings.claim.maxy");
        }

        Region region = new Region(old.getName(), old.getAdmins(), old.getMembers(), old.getLeaders(), new int[]{loc1.getBlockX(), loc1.getBlockX(), loc2.getBlockX(), loc2.getBlockX()}, new int[]{loc1.getBlockZ(), loc1.getBlockZ(), loc2.getBlockZ(), loc2.getBlockZ()}, miny, maxy, old.getPrior(), w.getName(), old.getDate(), old.getFlags(), old.getWelcome(), old.getValue(), old.getTPPoint(), old.canDelete());

        region.setPrior(RPUtil.getUpdatedPrior(region));

        String pName = p.getUniqueId().toString();
        if (!RedProtect.get().OnlineMode) {
            pName = p.getName().toLowerCase();
        }

        int pLimit = RedProtect.get().ph.getPlayerBlockLimit(p);
        int totalArea = RedProtect.get().rm.getTotalRegionSize(pName, p.getWorld().getName());
        boolean areaUnlimited = RedProtect.get().ph.hasPerm(p, "redprotect.limit.blocks.unlimited");
        int regionarea = RPUtil.simuleTotalRegionSize(RPUtil.PlayerToUUID(p.getName()), region);
        int actualArea = 0;
        if (regionarea > 0) {
            actualArea = totalArea + regionarea;
        }
        if (pLimit >= 0 && actualArea > pLimit && !areaUnlimited) {
            this.setError(p, RPLang.get("regionbuilder.reach.limit"));
            return;
        }

        Set<String> othersName = new HashSet<>();
        Region otherrg;

        //check if same area
        otherrg = RedProtect.get().rm.getTopRegion(region.getCenterLoc());
        if (otherrg != null && !checkID(region, otherrg) && otherrg.get4Points(region.getCenterY()).equals(region.get4Points(region.getCenterY()))) {
            this.setError(p, RPLang.get("regionbuilder.region.overlapping").replace("{location}", "x: " + otherrg.getCenterX() + ", z: " + otherrg.getCenterZ()).replace("{player}", RPUtil.UUIDtoPlayer(otherrg.getLeadersDesc())));
            return;
        }

        boolean hasAny = false;

        //check regions inside region
        for (Region r : RedProtect.get().rm.getInnerRegions(region, p.getWorld())) {
            if (!r.isLeader(p) && !p.hasPermission("redprotect.bypass")) {
                this.setError(p, RPLang.get("regionbuilder.region.overlapping").replace("{location}", "x: " + r.getCenterX() + ", z: " + r.getCenterZ()).replace("{player}", RPUtil.UUIDtoPlayer(r.getLeadersDesc())));
                return;
            }
            if (checkID(region, r)) {
                hasAny = true;
                continue;
            }
            othersName.add(r.getName());
        }

        //check borders for other regions
        Set<Location> limitlocs = region.getLimitLocs(region.getMinY(), region.getMaxY(), true);
        for (Location loc : limitlocs) {
            otherrg = RedProtect.get().rm.getTopRegion(loc);
            RedProtect.get().logger.debug("protection Block is: " + loc.getBlock().getType().name());

            if (otherrg != null) {
                if (checkID(region, otherrg)) {
                    hasAny = true;
                    continue;
                }

                if (!otherrg.isLeader(p) && !p.hasPermission("redprotect.bypass")) {
                    this.setError(p, RPLang.get("regionbuilder.region.overlapping").replace("{location}", "x: " + otherrg.getCenterX() + ", z: " + otherrg.getCenterZ()).replace("{player}", RPUtil.UUIDtoPlayer(otherrg.getLeadersDesc())));
                    return;
                }
                othersName.add(otherrg.getName());
            }
        }

        if (!hasAny) {
            this.setError(p, RPLang.get("regionbuilder.needinside"));
            return;
        }

        if (RPConfig.getEcoBool("claim-cost-per-block.enable") && RedProtect.get().Vault && !p.hasPermission("redprotect.eco.bypass")) {
            double peco = RedProtect.get().econ.getBalance(p);
            long reco = (region.getArea() <= old.getArea() ? 0 : region.getArea() - old.getArea()) * RPConfig.getEcoInt("claim-cost-per-block.cost-per-block");

            if (!RPConfig.getEcoBool("claim-cost-per-block.y-is-free")) {
                reco = reco * Math.abs(region.getMaxY() - region.getMinY());
            }

            if (peco >= reco) {
                RedProtect.get().econ.withdrawPlayer(p, reco);
                p.sendMessage(RPLang.get("economy.region.claimed").replace("{price}", RPConfig.getEcoString("economy-symbol") + reco + " " + RPConfig.getEcoString("economy-name")));
            } else {
                RPLang.sendMessage(p, RPLang.get("regionbuilder.notenought.money").replace("{price}", RPConfig.getEcoString("economy-symbol") + reco));
                return;
            }
        }

        RedProtect.get().rm.remove(old, w);

        int claimLimit = RedProtect.get().ph.getPlayerClaimLimit(p);
        int claimused = RedProtect.get().rm.getPlayerRegions(p.getName(), w);
        boolean claimUnlimited = RedProtect.get().ph.hasPerm(p, "redprotect.limit.claim.unlimited");

        p.sendMessage(RPLang.get("general.color") + "------------------------------------");
        p.sendMessage(RPLang.get("regionbuilder.claim.left") + (claimused + 1) + RPLang.get("general.color") + "/" + (claimUnlimited ? RPLang.get("regionbuilder.area.unlimited") : claimLimit));
        p.sendMessage(RPLang.get("regionbuilder.area.used") + " " + (regionarea == 0 ? ChatColor.GREEN + "" + regionarea : ChatColor.RED + "- " + regionarea) + "\n" +
                RPLang.get("regionbuilder.area.left") + " " + (areaUnlimited ? RPLang.get("regionbuilder.area.unlimited") : (pLimit - actualArea)));
        p.sendMessage(RPLang.get("cmdmanager.region.priority.set").replace("{region}", region.getName()) + " " + region.getPrior());
        p.sendMessage(RPLang.get("general.color") + "------------------------------------");
        if (othersName.size() > 0) {
            p.sendMessage(RPLang.get("general.color") + "------------------------------------");
            p.sendMessage(RPLang.get("regionbuilder.overlapping"));
            p.sendMessage(RPLang.get("region.regions") + " " + othersName);
        }

        this.r = region;
        RedProtect.get().logger.addLog("(World " + region.getWorld() + ") Player " + p.getName() + " REDEFINED region " + region.getName());
    }

    private boolean checkID(Region newr, Region oldr) {
        return newr.getID().equals(oldr.getID());
    }
}
