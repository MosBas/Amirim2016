package bots;

import java.util.ArrayList;
import java.util.List;

import com.sun.prism.impl.Disposer.Target;

import pirates.game.Location;
import pirates.game.Powerup;
import pirates.game.Treasure;
import pirates.game.Pirate;
import pirates.game.PirateBot;
import pirates.game.PirateGame;

public class MyBot implements PirateBot {
	
	private class Turn {
		public double score;
		public State state;
		public Pirate self;
		
		public boolean defend = false;
		public Pirate enemy = null;
		public Location target = null;
		
		public Turn(State state) {
			this.state = state;
			this.score = 0;
		}
		
		public Turn(Pirate self, State state, double score) { // defend constructor
			this.self = self;
			this.state = state;
			defend = true;
			this.score = score;
		}
		
		public Turn(Pirate self, State state, Pirate enemy, double score) { // attack constructor
			this.self = self;
			this.state = state;
			this.enemy = enemy;
			this.score = score;
		}
		
		public Turn(Pirate self, State state, Location target, double score) { // move constructor
			this.self = self;
			this.state = state;
			this.target = target;
			this.score = score;
		}
	}
	
	private void execTurn(PirateGame game, Turn turn) {
		if (turn.defend) {
			game.defend(turn.self);
			return;
		}
		
		if (turn.enemy != null) {
			game.attack(turn.self, turn.enemy);
			return;
		}
		
		if (turn.target != null) {
			game.setSail(turn.self, turn.target);
			return;
		}
	}
	
	private class State {
		public int movesBank = 0;
		public List<Location> occupiedTargets = null;
		public List<Pirate> attackedEnemies = null;
		public List<Location> targetTreasures = null;
		public Pirate attacker = null;
		
		public State() {}
		
		public State(State other) {
			this.movesBank = other.movesBank;
			this.occupiedTargets = other.occupiedTargets;
			this.attackedEnemies = other.attackedEnemies;
			this.targetTreasures = other.targetTreasures;
		}
		
		public State(State other, List<Pirate> newAttackedEnemies) {
			this(other);
			this.attackedEnemies = newAttackedEnemies;
		}
	}
	
	private void addMoveTurn(List<Turn> turns, State state, Pirate pirate, Location target, int moves, double score) {
		if (state.movesBank < moves)
			return;
		
		if (game.distance(target, pirate.getLocation()) < moves)
			return;
		
		List<Location> possibleLocations = game.getSailOptions(pirate, target, moves);
		
		List<Location> untakenLocations = new ArrayList<Location>();
		for (Location loc : possibleLocations) {
			if (!untakenLocations.contains(loc) && !game.isOccupied(loc) && !state.occupiedTargets.contains(loc)) {
				untakenLocations.add(loc);
			}
		}
		
		if (untakenLocations.size() == 0)
			return;
		
		Location loc = untakenLocations.get(0);
		
		boolean isTreasure = false;
		for (Treasure t : game.treasures())
			if (t.getLocation().equals(target))
				isTreasure = true;
		
//			actionsDone++;
		State newState = new State(state);
		newState.occupiedTargets = new ArrayList<Location>(state.occupiedTargets);
		newState.occupiedTargets.add(loc);
		
		if (isTreasure && !state.targetTreasures.contains(target)) {
			newState.targetTreasures = new ArrayList<Location>(state.targetTreasures);
			newState.targetTreasures.add(target);
		}

		newState.movesBank -= moves;
		
		turns.add(new Turn(pirate, newState, loc, score * moves / (game.distance(loc, target) + 1)));
	}
	
	private List<Turn> allTurns(Pirate pirate, State state) {
		List<Turn> turns = new ArrayList<Turn>();
		turns.add(new Turn(state));
		
		if (pirate.getTurnsToSober() > 0 || pirate.isLost()) { // drunk or lost
			return turns;
		}
		
		Pirate enemyToAttack = findEnemyToAttack(pirate, game, state);
		if (enemyToAttack != null) { // there is someone to attack
//			actionsDone++;
			int score = 1000;
			
			List<Pirate> newAttackedEnemies = new ArrayList<Pirate>(state.attackedEnemies);
			newAttackedEnemies.add(enemyToAttack);
			
			turns.add(new Turn(pirate, new State(state, newAttackedEnemies), enemyToAttack, score));
		}
		
		if (shouldDefend(pirate, game)) {
			int score = 1000;
			
			turns.add(new Turn(pirate, state, score));
		}
		
		for (int moves = state.movesBank; moves >= 1; --moves) {
			if (pirate.getId() == 0) {
				int score = 1000000;
				List<Pirate> enemies = game.enemyPiratesWithTreasures().size() > 0 ? game.enemyPiratesWithTreasures() : game.enemyPirates();
				Location enemyBase = enemies.get(0).getInitialLocation();
				
				
				if (pirate.getLocation().equals(enemyBase)) {
					List<Pirate> newAttackedEnemies = new ArrayList<Pirate>(state.attackedEnemies);
					newAttackedEnemies.add(enemyToAttack);
					
					turns.add(new Turn(pirate, new State(state, newAttackedEnemies), enemyToAttack, score));
				} else {
					addMoveTurn(turns, state, pirate, enemyBase, moves, score);
				}
			}
			
			if (pirate.hasTreasure() && moves <= pirate.getCarryTreasureSpeed()) {
				int score = 1000; // should depend on moves
				
				Location target = pirate.getInitialLocation();
				addMoveTurn(turns, state, pirate, target, moves, score);
			} else if (!pirate.hasTreasure()) {
				Pirate closestEnemy = findClosestShip(game, pirate, game.enemySoberPirates());
				if (closestEnemy != null && pirate.getReloadTurns() == 0) {
					int score = 15; // should depend on moves and distance something
					Location target = closestEnemy.getLocation();			
					addMoveTurn(turns, state, pirate, target, moves, score);					
				}
				
				Pirate closestEnemyWithTreasure = findClosestShip(game, pirate, game.enemyPiratesWithTreasures());
				if (!alreadyShot.isEmpty() && pirate.getReloadTurns() != 0
						&& closestEnemyWithTreasure != null && !pirate.hasTreasure()) {
					int score = 30; // should depend on moves and distance something
					Location target = enemyNextLocation(game, closestEnemyWithTreasure, closestEnemyWithTreasure.getInitialLocation());
					addMoveTurn(turns, state, pirate, target, moves, score);					
				}
				
				Treasure bestTreasure = findBestTreasure(game, pirate, state);
				if (bestTreasure != null) {
					int score = 20; // should depend on moves and distance something
	
					Location target = bestTreasure.getLocation();			
					addMoveTurn(turns, state, pirate, target, moves, score);
				}
			}
		}
		
		return turns;
	}
	
	private double totalScore(List<Turn> turns) {
		double score = 0;
		
		for (Turn t : turns) {
			score += t.score;
		}
		
		return score;
	}
	
	private List<Turn> bestTurns(List<Pirate> pirates, State state, int level) {
		if (pirates.isEmpty()) {
			return new ArrayList<Turn>();
		}
		
		List<Turn> turns;
		List<Turn> optimal = null;
		double optimalScore = -1;
		
		List<Turn> possibleTurns = allTurns(pirates.get(0), state);
		
		for (Turn currTurn : possibleTurns) {
			List<Pirate> subList = new ArrayList<Pirate>(pirates);
			subList.remove(0);
			
			List<Turn> subTurns = bestTurns(subList, currTurn.state, level + 1);
			turns = new ArrayList<Turn>(subTurns);
			turns.add(currTurn);
			
			double currScore = totalScore(turns); 
			if (currScore > optimalScore) {
				optimal = turns;
				optimalScore = currScore; 
			}
		}
		
		return optimal;
	}
	
	private PirateGame game;
	List<Pirate> alreadyShot;
	
	@Override
	public void doTurn(PirateGame game) {
		this.game = game;
		
		State state = new State();
		state.movesBank = game.getActionsPerTurn();
		state.occupiedTargets = new ArrayList<Location>();
		state.attackedEnemies = new ArrayList<Pirate>();
		state.targetTreasures = new ArrayList<Location>();
		
		alreadyShot = new ArrayList<Pirate>();
		List<Pirate> myPiratesWithTreasures = game.myPiratesWithTreasures();
		List<Pirate> myPiratesWithoutTreasures = game.myPiratesWithoutTreasures();

		for (Pirate p : myPiratesWithoutTreasures)
			if (p.getReloadTurns() != 0)
				alreadyShot.add(p);
		
		List<Pirate> pirates = new ArrayList<>(myPiratesWithTreasures);  // pirates with treasures get to go first
		pirates.addAll(myPiratesWithoutTreasures);
		
		List<Turn> gameTurns = bestTurns(pirates, state, 0);
		game.debug("final score: " + totalScore(gameTurns));
		for (Turn t : gameTurns)
			execTurn(game, t);
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
		Location myShipLocation = pirate.getLocation();
		Pirate result = null;
		Pirate enemyWithTreasure = null;
		int minDistance = Integer.MAX_VALUE;

		for (Pirate enemy : pirates) {
			if (enemy.getLocation().col == enemy.getInitialLocation().col &&
					enemy.getLocation().row == enemy.getInitialLocation().row) {
				continue;
			}
			int distance = game.distance(myShipLocation, enemy.getLocation());
			if (distance < minDistance) {
				minDistance = distance;
				result = enemy;
				if (result.hasTreasure())
					enemyWithTreasure = result;
			}

		}
		if (enemyWithTreasure != null) {
			result = enemyWithTreasure;
		}
		return result;
	}

	private Treasure findBestTreasure(PirateGame game, Pirate pirate, State state) {
		Location myShipLocation = pirate.getLocation();
		Treasure result = null;
		double maxRatio = 0;

		for (Treasure treasure : game.treasures()) {
			if (state == null) {
				game.debug("state is null");
			}
			
			if (state.targetTreasures == null) {
				game.debug("target treasures is null");
			}
			if (state.targetTreasures.contains(treasure.getLocation()))
				continue;
			
			double distance = game.distance(myShipLocation, treasure.getLocation());
			double value = treasure.getValue();
			double ratio = value / distance;

			if (ratio > maxRatio) {
				maxRatio = ratio;
				result = treasure;
			}
		}

		return result;
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

	private Pirate findEnemyToAttack(Pirate ship, PirateGame game, State state) {
		for (Pirate enemy : game.enemySoberPirates()) {
			if (state.attackedEnemies.contains(enemy)) {
				continue;
			}
			if (enemy != null && ship.getReloadTurns() == 0 && game.inRange(ship, enemy) && !ship.hasTreasure() &&
					enemy.getDefenseExpirationTurns() == 0) {
				return enemy;
			}
		}
		return null;
	}

	private boolean shouldDefend(Pirate ship, PirateGame game) {
		for (Pirate enemy : game.enemySoberPirates()) {
			if (enemy != null && enemy.getReloadTurns() == 0 && !enemy.hasTreasure() &&
					ship.getDefenseReloadTurns() == 0 && game.inRange(ship, enemy)) {
				return true;
			}
		}
		return false;
	}
}
