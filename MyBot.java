package bots;

import java.nio.file.ClosedWatchServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;

import pirates.game.Direction;
import pirates.game.Location;
import pirates.game.Powerup;
import pirates.game.Treasure;
import pirates.game.Pirate;
import pirates.game.PirateBot;
import pirates.game.PirateGame;

public class MyBot implements PirateBot {

	@Override
	public void doTurn(PirateGame game) {
		int movesBank = game.getActionsPerTurn();

		List<Pirate> alreadyshot = new ArrayList<Pirate>();
		List<Pirate> l1 = game.myPiratesWithTreasures();
		List<Pirate> l2 = game.myPiratesWithoutTreasures();

		for (Pirate p : l2)
			if (p.getReloadTurns() != 0)
				alreadyshot.add(p);

		List<Pirate> pirates = new ArrayList<>(l1);
		pirates.addAll(l2);

		List<Location> takenLocations = new ArrayList<Location>();
		List<Location> occupiedTargets = new ArrayList<Location>();

		game.debug("Number of pirates is " + game.myPirates().size());
		
		if (game.myPirates().size() == 1) {
			int moves = 0;
			Location location1 = null;
			Pirate pirate = pirates.remove(0);
			if (pirate.getReloadTurns() == 0 && game.inRange(pirate, game.getEnemyPirate(0))) {
				game.attack(pirate, game.getEnemyPirate(0));
			} else {
				if (pirate.hasTreasure()) {
					location1 = pirate.getInitialLocation();
					moves = 1;
				} else if (pirate.getReloadTurns() != 0) {
					location1 = game.treasures().get(0).getLocation();
					moves = 6;
				} else {
					location1 = new Location(13, 16);
					moves = 6;
				}
				if (location1 != null) {
					List<Location> possibleLocations = game.getSailOptions(pirate, location1, moves);
					for (Location loc : possibleLocations) {
						if (!takenLocations.contains(loc) && (!game.isOccupied(loc))) {
							takenLocations.add(loc);
							game.setSail(pirate, loc);
							break;
						}
					}

				}

			}
		} else if (game.myPirates().size() == 2 && game.enemyPirates().size() == 2) {
			Pirate attacker = null;
			while (!pirates.isEmpty()) {
				boolean goanyway = false;
				int move = 0;
				Pirate pirate1 = pirates.remove(0);
				Location location = null;
				Pirate ClosestEnemy = findClosestShip(game, pirate1, game.enemySoberPirates());
				Pirate closestEnemyWithTreasure = findClosestShip(game, pirate1, game.enemyPiratesWithTreasures());
				String[] power = pirate1.getPowerups();
				Pirate enemyPirate = enemyWithPowerup(game);
				if (pirate1.getTurnsToSober() == 0 && !pirate1.isLost()) {
					if (pirate1.getReloadTurns() == 0 && game.inRange(pirate1, ClosestEnemy)
							&& !pirate1.hasTreasure()) {
						game.debug("Pirate " + pirate1.getId() + " is attacking enemy " + ClosestEnemy.getId());
						game.attack(pirate1, ClosestEnemy);
					} else if (ClosestEnemy != null && ClosestEnemy.getReloadTurns() == 0 && !ClosestEnemy.hasTreasure()
							&& pirate1.getDefenseReloadTurns() == 0 && game.inRange(pirate1, ClosestEnemy)) {
						game.defend(pirate1);
						game.debug("Pirate " + pirate1.getId() + " is defending from enemy " + ClosestEnemy.getId());
					}
					/*
					 * else if(enemyPirate!=null) { Pirate kamikaze
					 * =findClosestShip(game, enemyPirate, game.myPirates());
					 * goanyway = true; if(kamikaze.compareTo(pirate1)==0) {
					 * location = enemyPirate.getLocation(); game.debug(
					 * "Pirate " + pirate1.getId() +
					 * " is intiiatng kamikaze on  " + enemyPirate.getId()); //
					 * we're limiting to only one attacker per turn move =
					 * Math.min(Math.min(3, movesBank),
					 * game.distance(pirate1.getLocation(), location)); } }
					 */
					else {
						if (pirate1.hasTreasure()) {
							game.debug("Pirate " + pirate1.getId() + " has treasure and is going home");
							location = pirate1.getInitialLocation();
							move = pirate1.getCarryTreasureSpeed();

						} else if (pirate1.getReloadTurns() == 0 && (attacker == null || game.treasures().isEmpty())) {
							attacker = pirate1;
							location = ClosestEnemy.getLocation();
							game.debug(
									"Pirate " + pirate1.getId() + " is going to attack enemy " + ClosestEnemy.getId()); // we're
																														// limiting
																														// to
																														// only
																														// one
																														// attacker
																														// per
																														// turn
							move = Math.min(Math.min(3, movesBank), game.distance(pirate1.getLocation(), location));
						} else if (!game.powerups().isEmpty()) {
							List<Powerup> p = game.powerups();
							while (!p.isEmpty()) {
								Powerup power2 = p.remove(0);
								Pirate min = closestToPowerup(power2, game);
								if (min.compareTo(pirate1) == 0) {
									location = power2.getLocation();
								}
							}
						}
						/*
						 * else if (!alreadyshot.isEmpty() &&
						 * pirate1.getReloadTurns() != 0 &&
						 * closestEnemyWithTreasure != null &&
						 * !pirate1.hasTreasure()) { location =
						 * enemyNextLocation(game, closestEnemyWithTreasure,
						 * closestEnemyWithTreasure.getInitialLocation()); move
						 * = Math.min(Math.min(3, movesBank),
						 * game.distance(pirate1.getLocation(), location));
						 * goanyway = true; game.debug("Pirate " +
						 * pirate1.getId() + " is going to sink enemy " +
						 * closestEnemyWithTreasure.getId()); } /*else if
						 * (game.myDrunkPirates().size() > 0) { List<Pirate> p =
						 * new ArrayList<Pirate>(); for (Pirate pir :
						 * game.myPiratesWithoutTreasures()) if
						 * (pir.getTurnsToSober() == 0) p.add(pir); for (int i =
						 * 0 ; i < game.myDrunkPirates().size(); i++) if
						 * (pirate1.compareTo(findClosestShip(game,
						 * game.myDrunkPirates().get(i), p)) == 0) { goanyway =
						 * true; location =
						 * game.myDrunkPirates().get(i).getLocation(); move =
						 * Math.min(Math.min(2, movesBank),
						 * game.distance(pirate1.getLocation(), location));
						 * game.debug("Pirate " + pirate1.getId() +
						 * " is going to help " +
						 * game.myDrunkPirates().get(i).getId()); } }
						 */
						else {
							List<Treasure> closesTreasure = game.treasures();
							Treasure closestTreasure = findBestTreasure(game, pirate1, closesTreasure);
							if (closestTreasure != null) {
								location = closestTreasure.getLocation();
								move = movesBank - game.myPiratesWithTreasures().size();
							}
						}
					}

					movesBank -= move;
					game.debug("movesBank = " + movesBank);
					if (movesBank > 0 && pirates.isEmpty() && !pirate1.hasTreasure()) {
						move = move + movesBank;
					}
					if (location != null) {
						List<Location> possibleLocations = game.getSailOptions(pirate1, location, move);
						for (Location loc : possibleLocations) {
							if (!takenLocations.contains(loc) && (!game.isOccupied(loc) || goanyway)
									&& (!occupiedTargets.contains(loc))) {
								takenLocations.add(loc);
							}
						}
						Location loc = findSafestLocation(game, takenLocations, game.allEnemyPirates());
						if (loc != null) {
							game.setSail(pirate1, loc);
							game.debug(
									"Pirates " + pirate1.getId() + " is going " + move + " steps to " + loc.toString());
							occupiedTargets.add(loc);
						}

					}
				}
			}

		} else {
			Pirate attacker = null;
			while (!pirates.isEmpty()) {
				boolean goanyway = false;
				int move = 0;
				Pirate pirate1 = pirates.remove(0);
				Location location = null;
				Pirate ClosestEnemy = findClosestShip(game, pirate1, game.enemySoberPirates());
				Pirate closestEnemyWithTreasure = findClosestShip(game, pirate1, game.enemyPiratesWithTreasures());
				
				// Pirate enemyPirate = enemyWithPowerup(game);

				if (pirate1.getTurnsToSober() == 0 && !pirate1.isLost()) {
					if (ClosestEnemy != null && pirate1.getReloadTurns() == 0 && game.inRange(pirate1, ClosestEnemy)
							&& !pirate1.hasTreasure()) {
						game.debug("Pirate " + pirate1.getId() + " is attacking enemy " + ClosestEnemy.getId());
						game.attack(pirate1, ClosestEnemy);
					} else if (ClosestEnemy != null && ClosestEnemy.getReloadTurns() == 0 && !ClosestEnemy.hasTreasure()
							&& pirate1.getDefenseReloadTurns() == 0 && game.inRange(pirate1, ClosestEnemy)) {
						game.defend(pirate1);
						game.debug("Pirate " + pirate1.getId() + " is defending from enemy " + ClosestEnemy.getId());
					} else {
						if (pirate1.hasTreasure()) {
							game.debug("Pirate " + pirate1.getId() + " has treasure and is going home");
							location = pirate1.getInitialLocation();
							move = pirate1.getCarryTreasureSpeed();
						} else if (ClosestEnemy != null && pirate1.getReloadTurns() == 0
								&& (attacker == null || game.treasures().isEmpty())) {
							attacker = pirate1;
							location = ClosestEnemy.getLocation();
							game.debug(
									"Pirate " + pirate1.getId() + " is going to attack enemy " + ClosestEnemy.getId());
							move = Math.min(Math.min(3, movesBank), game.distance(pirate1.getLocation(), location));
						} else if (!alreadyshot.isEmpty() && pirate1.getReloadTurns() != 0
								&& closestEnemyWithTreasure != null && !pirate1.hasTreasure()) {
							location = enemyNextLocation(game, closestEnemyWithTreasure,
									closestEnemyWithTreasure.getInitialLocation());
							move = Math.min(Math.min(3, movesBank), game.distance(pirate1.getLocation(), location));
							goanyway = true;
							game.debug("Pirate " + pirate1.getId() + " is going to sink enemy with treasure"
									+ closestEnemyWithTreasure.getId());
						}

						else {
							List<Treasure> closesTreasure = game.treasures();
							Treasure closestTreasure = findBestTreasure(game, pirate1, closesTreasure);
							
							if (closestTreasure != null) {
								location = closestTreasure.getLocation();
								game.debug("Pirate " + pirate1.getId() + " is going to get treasure");
								move = Math.min(Math.min(3, movesBank), game.distance(pirate1.getLocation(), location));
							}
						}
					}

					movesBank -= move;
					if (movesBank > 0 && pirates.isEmpty() && !pirate1.hasTreasure()) {
						move = move + movesBank;
						game.debug("movesBank = " + movesBank);
					}
					
					if (location != null) {
						List<Location> possibleLocations = game.getSailOptions(pirate1, location, move);
						for (Location loc : possibleLocations) {
							if (!takenLocations.contains(loc) && (!game.isOccupied(loc) || goanyway)
									&& (!occupiedTargets.contains(loc))) {
								takenLocations.add(loc);
							}
						}
						Location loc = findSafestLocation(game, takenLocations, game.allEnemyPirates());
						if (loc != null) {
							game.setSail(pirate1, loc);
							game.debug(
									"Pirate " + pirate1.getId() + " is going " + move + " steps to " + loc.toString());
							game.debug("movesBank = " + movesBank);
							occupiedTargets.add(loc);
						}
					}
				} else
					game.debug("Pirate " + pirate1.getId() + " is drunk or lost");
			}
		}
	}

	private static Pirate enemyWithPowerup(PirateGame game) {
		List<Pirate> enemys = game.enemyPirates();
		Pirate p = null;
		while (!enemys.isEmpty()) {
			p = enemys.remove(0);
			if (p.getPowerups().length > 0)
				if (p.getPowerups()[0] != null || p.getPowerups()[1] != null)
					return p;
		}
		p = null;
		return p;

	}

	public static Location enemyNextLocation(PirateGame game, Pirate enemy, Location finishLocation) {
		List<Location> possibleLocations = game.getSailOptions(enemy, finishLocation, 1);
		Location location = possibleLocations.get(0);
		return location;
	}

	public static Pirate recentlyShot(PirateGame game, List<Pirate> pirates) {
		Pirate Pirates = null;
		int maxReloadTurns = 1;
		if (!pirates.isEmpty()) {
			Pirates = pirates.remove(0);
			maxReloadTurns = Pirates.getReloadTurns();
		}

		while (!pirates.isEmpty()) {
			Pirate tempPirate = pirates.remove(0);
			int tempmaxReloadTurns = tempPirate.getReloadTurns();
			if (tempmaxReloadTurns > maxReloadTurns) {
				maxReloadTurns = tempmaxReloadTurns;
				Pirates = tempPirate;
			}
		}
		return Pirates;

	}

	public static Location findSafestLocation(PirateGame game, List<Location> loc, List<Pirate> pir) {
		Location location = null;
		int minDistance = 0;
		if (!loc.isEmpty()) {
			location = loc.remove(0);
			minDistance = findFarestLoaction(game, location, pir);
		}

		while (!loc.isEmpty()) {
			Location tempLocation = loc.remove(0);
			int tempDistance = findFarestLoaction(game, tempLocation, pir);
			if (tempDistance < minDistance) {
				minDistance = tempDistance;
				location = tempLocation;
			}
		}
		return location;
	}

	public static int findFarestLoaction(PirateGame game, Location location, List<Pirate> pirates) {
		Pirate Pirates = null;
		int maxDistance = 0;
		if (!pirates.isEmpty()) {
			Pirates = pirates.remove(0);
			maxDistance = game.distance(location, Pirates.getLocation());
		}

		while (!pirates.isEmpty()) {
			Pirate tempPirate = pirates.remove(0);
			int tempDistance = game.distance(location, tempPirate.getLocation());
			if (tempDistance > maxDistance) {
				maxDistance = tempDistance;
			}
		}
		return maxDistance;

	}

	public static Pirate findClosestShip(PirateGame game, Pirate pirate, List<Pirate> pirates) {
		List<Pirate> closesPirate = pirates;
		Location myShipLocation = pirate.getLocation();
		Pirate Pirates = null;
		Pirate PirateWithTreasure = null;
		int minDistance = 0;
		if (!closesPirate.isEmpty()) {
			Pirates = closesPirate.remove(0);
			minDistance = game.distance(myShipLocation, Pirates.getLocation());
		}

		while (!closesPirate.isEmpty()) {
			Pirate tempPirate = closesPirate.remove(0);
			int tempDistance = game.distance(myShipLocation, tempPirate.getLocation());
			if (tempDistance < minDistance) {
				minDistance = tempDistance;
				Pirates = tempPirate;
				if (Pirates.hasTreasure())
					PirateWithTreasure = Pirates;
			}

		}
		if (PirateWithTreasure != null)
			return PirateWithTreasure;
		return Pirates;
	}

	public static Treasure findBestTreasure(PirateGame game, Pirate pirate, List<Treasure> closesTreasure) {
		if (closesTreasure == null)
			return null;

		Location myShipLocation = pirate.getLocation();
		Treasure treasure = null;
		double maxRatio = 0;

		while (!closesTreasure.isEmpty()) {
			Treasure tempTreasure = closesTreasure.remove(0);
			int tempDistance = game.distance(myShipLocation, tempTreasure.getLocation());
			int value = tempTreasure.getValue();

			double ratio = value / tempDistance;

			if (ratio > maxRatio) {
				maxRatio = ratio;
				treasure = tempTreasure;
			}
		}

		return treasure;
	}

	public static Pirate closestToPowerup(Powerup p, PirateGame game) {
		List<Pirate> myPirate = game.mySoberPirates();
		Pirate minPirate = null;
		int minDistance = 0;
		if (!myPirate.isEmpty()) {
			minPirate = myPirate.remove(0);
			minDistance = game.distance(minPirate.getLocation(), p.getLocation());
		}
		while (!myPirate.isEmpty()) {
			Pirate tempPirate = myPirate.remove(0);
			int tempDist = game.distance(tempPirate.getLocation(), p.getLocation());
			if (tempDist < minDistance) {
				minDistance = tempDist;
				minPirate = tempPirate;
			}
		}
		return minPirate;
	}
}
