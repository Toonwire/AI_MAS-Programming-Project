package statespace;

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import statespace.Strategy.*;
import statespace.Command.Dir;
import statespace.Command.Type;
import statespace.Heuristic.*;

public class AIClient {
	private static int MAX_ROW;
	private static int MAX_COL;

	private static boolean[][] walls;
	private boolean[][] tempWalls;
	private static Goal[][] goals;
	private static Agent[][] agents;
	private static Agent[] agentIDs;
	private static Box[][] boxes;
	private static int agentCounter = 0;

	private List<Goal> goalList;
	private Map<String, Box[][]> boxMap = new HashMap<>();
	private Map<String, Goal[][]> goalMap = new HashMap<>();
	private Map<Goal, String> goalColor = new HashMap<>();
	// private Map<Character, ArrayList<Goal>> goalMap;
	private static Map<Agent, Node> initialStates = new HashMap<>();

	private Map<Character, ArrayList<Goal>> goalListMap = new HashMap<>();
	private Map<Goal, Integer[][]> dijkstraMap = new HashMap<>();
	
	private final static Set<String> COLORS = new HashSet<>(
			Arrays.asList("blue", "red", "green", "cyan", "magenta", "orange", "pink", "yellow"));
	private static final String DEFAULT_COLOR = "blue";


	// private BufferedReader in = new BufferedReader(new
	// InputStreamReader(System.in));
	// private List<Agent> agents = new ArrayList<Agent>();

	private AIClient(BufferedReader in) throws IOException {
		Map<Character, String> chrColorMap = new HashMap<>();
		Map<String, List<Character>> colorChrMap = new HashMap<>();
		Map<String, Integer> colorAgents = new HashMap<>();
		Map<String, LinkedList<Goal>> colorGoals = new HashMap<>();
		
		String line, color;

		// Read lines specifying colors
		while ((line = in.readLine()).matches("^[a-z]+:\\s*[0-9A-Z](,\\s*[0-9A-Z])*\\s*$")) {
			line = line.replaceAll("\\s", "");
			color = line.split(":")[0];
			if (!COLORS.contains(color))
				throw new ColorException("Color not defined");
//			else if (colorChrMap.containsKey(color))
//				throw new ColorException("Color defined multiple times..");

			List<Character> colorObjects = new ArrayList<>();
			if (colorChrMap.containsKey(color)) {
				colorObjects = colorChrMap.get(color);
			}
			for (String id : line.split(":")[1].split(",")) {
				colorObjects.add(id.charAt(0));
				chrColorMap.put(id.charAt(0), color);
			}
			colorChrMap.put(color, colorObjects);
		}

		// Max columns and rows
		MAX_COL = line.length();
		LinkedList<String> lines = new LinkedList<>();
		while (!line.equals("")) {
			lines.add(line);
			line = in.readLine();
			MAX_COL = line.length() > MAX_COL ? line.length() : MAX_COL;
		}
		MAX_ROW = lines.size();

		// Initialize arrays
		boxMap.put(DEFAULT_COLOR, new Box[MAX_ROW][MAX_COL]);
		goalMap.put(DEFAULT_COLOR, new Goal[MAX_ROW][MAX_COL]);
		for (String currentColor : colorChrMap.keySet()) {
			boxMap.put(currentColor, new Box[MAX_ROW][MAX_COL]);
			goalMap.put(currentColor, new Goal[MAX_ROW][MAX_COL]);
		}

		walls = new boolean[MAX_ROW][MAX_COL];
		tempWalls = new boolean[MAX_ROW][MAX_COL];
		agents = new Agent[MAX_ROW][MAX_COL];
		goals = new Goal[MAX_ROW][MAX_COL];
		boxes = new Box[MAX_ROW][MAX_COL];
		goalList = new ArrayList<Goal>();
		// goalMap = new HashMap<Character, ArrayList<Goal>>();
		
		// Read lines specifying level layout
		for (int row = 0; row < lines.size(); row++) {
			line = lines.get(row);

			for (int col = 0; col < line.length(); col++) {
				char chr = line.charAt(col);

				if (chr == '+') { // Wall.
					walls[row][col] = true;
				} else if ('0' <= chr && chr <= '9') { // Agent.
					String c = chrColorMap.get(chr);
					if (c == null)
						c = DEFAULT_COLOR;

					Agent a = new Agent(chr, c, this);
					agents[row][col] = a;
					
					if(colorAgents.get(c) == null) {
						colorAgents.put(c, 0);
					}
					colorAgents.put(c, colorAgents.get(c)+1);
					agentCounter++;
				} else if ('A' <= chr && chr <= 'Z') { // Box.
					String c = chrColorMap.get(chr);
					if (c == null)
						c = DEFAULT_COLOR;

					Box box = new Box(chr,c);
					boxes[row][col] = box;
					boxMap.get(c)[row][col] = box;
				} else if ('a' <= chr && chr <= 'z') { // Goal.
					String c = chrColorMap.get(Character.toUpperCase(chr));
					if (c == null)
						c = DEFAULT_COLOR;

					Goal goal = new Goal(chr, new Pos(row, col));
					goals[row][col] = goal;
					goalMap.get(c)[row][col] = goal;
					goalColor.put(goal, c);

					goalList.add(goal);

					if(colorGoals.get(c) == null) {
						colorGoals.put(c, new LinkedList<Goal>());
					}
					colorGoals.get(c).add(goal);
					
					if (!goalListMap.containsKey(chr)) goalListMap.put(chr, new ArrayList<Goal>());
					goalListMap.get(chr).add(goal);
					
					// if (!goalMap.containsKey(chr))
					// goalMap.put(chr, new ArrayList<Goal>());
					// goalMap.get(chr).add(goal);
				} else if (chr == ' ') {
					// Free space.
				} else {
					System.err.println("Error, read invalid level character: " + chr);
					System.exit(1);
				}
			}
		}
		

		for (Map.Entry<Character, ArrayList<Goal>> entry : goalListMap.entrySet()) {
			ArrayList<Goal> goals = entry.getValue();
			
			for (Goal goal : goals) {
				
				Integer[][] dijkstra = new Integer[MAX_ROW][MAX_COL];
				
				// Pair of currentPos, corresponding parentPos
				LinkedList<Pair<Pos,Pos>> queue = new LinkedList<>();
				
				queue.add(new Pair<Pos, Pos>(goal.getPos(), null));
				
				while(true) {	
					
					if (queue.isEmpty()) break;
					
					Pair<Pos, Pos> pair = queue.poll();
					Pos pos = (Pos) pair.getLeft();
					Pos parentPos = (Pos) pair.getRight();
					
					if (dijkstra[pos.row][pos.col] != null || walls[pos.row][pos.col]) continue;
					
					if (parentPos == null) {
						dijkstra[pos.row][pos.col] = 0;
					} else {
						dijkstra[pos.row][pos.col] = dijkstra[parentPos.row][parentPos.col] + 1;
					}
					
					if (0 <= pos.row - 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row - 1, pos.col), pos));
					if (MAX_ROW > pos.row + 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row + 1, pos.col), pos));
					if (0 <= pos.col- 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col - 1), pos));
					if (MAX_COL > pos.col + 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col + 1), pos));
				}
				dijkstraMap.put(goal, dijkstra);
			}
		}
		
		//Create initial state
		combinedSolution = new LinkedList<>();
		state = new MultiNode(this, boxes, agents);
		combinedSolution.add(state);
		
		//Create initial states
		agentIDs = new Agent[getAgentNum()];
		for (int row = 0; row < MAX_ROW; row++) {
			for (int col = 0; col < MAX_COL; col++) {
				Agent a = agents[row][col];
				if (a != null) {
					agentIDs[a.getID()] = a;
					String c = a.getColor();
					Node initialState = new Node(null, this, a, new Pos(row, col), null);
					
					//Give reachable boxes
					LinkedList<Box> reachableBoxesList = new LinkedList<>();
					Box[][] reachableBoxes = new Box[MAX_ROW][MAX_COL];
					for (int row2 = 0; row2 < MAX_ROW; row2++) {
						for (int col2 = 0; col2 < MAX_COL; col2++) {
							Box[][] bsTemp = new Box[MAX_ROW][MAX_COL]; //array with single goal to test
							Box b = boxes[row2][col2];
							if(b != null && a.getColor().equals(b.getColor())) {
								bsTemp[row2][col2] = b;
								initialState.boxes = bsTemp;
								initialState.boxPosition = new Pos(row2, col2);
								LinkedList<Node> sol = search(getStrategy("bfs", initialState), initialState);
								initialState.boxPosition = null;
								if(sol != null && !sol.isEmpty()) {
									reachableBoxes[row2][col2] = b; //add to final box array
									reachableBoxesList.add(b);
								}
							}
						}
					}
					initialState.boxes = reachableBoxes;
//					initialState.boxes = boxMap.get(c);
					a.setReachableBoxes(reachableBoxesList);
					
					
					
					//If agents of same color, only solve for reachable goals 
					if(colorAgents.get(c) > 1) {
						Goal[][] reachableGoals = new Goal[MAX_ROW][MAX_COL];
						LinkedList<Goal> reachableGoalsList = new LinkedList<>();
						for(Goal g : colorGoals.get(c)) {
							Goal[][] gsTemp = new Goal[MAX_ROW][MAX_COL]; //array with single goal to test
							int row2 = g.getPos().row;
							int col2 = g.getPos().col;
							gsTemp[row2][col2] = g;
							initialState.goals = gsTemp;
							LinkedList<Node> sol = search(getStrategy("astar", initialState), initialState);
							if(sol != null && !sol.isEmpty()) {
								reachableGoals[row2][col2] = g; //add to final goal array
								reachableGoalsList.add(g);
							}
						}
						a.reachableGoals = reachableGoalsList;
						initialState.goals = reachableGoals;
					} else {
						initialState.goals = goalMap.get(c);
						a.reachableGoals = colorGoals.get(c);
					}
					initialStates.put(a, initialState);
//					System.err.println(a+"'s reachable boxes: "+reachableBoxesList+"\n "+initialState);
				}
			}
		}
	}


	private static Strategy getStrategy(String strategyStr, Node initialState) {
		Strategy strategy;
		
		switch (strategyStr.toLowerCase()) {
	        case "bfs":
	            strategy = new StrategyBFS();
	            break;
	        case "dfs":
	            strategy = new StrategyDFS();
	            break;
	        case "astar":
	            strategy = new StrategyBestFirst(new AStar(initialState));
	            break;
	        case "wastar":
	            // You're welcome to test WA* out with different values, but for the report you must at least indicate benchmarks for W = 5.
	            strategy = new StrategyBestFirst(new WeightedAStar(initialState, 5));
	            break;
	        case "greedy":
	            strategy = new StrategyBestFirst(new Greedy(initialState));
	            break;
	        default:
	            strategy = new StrategyBFS();
	            System.err.println("Defaulting to BFS search. Use arguments -bfs, -dfs, -astar, -wastar, or -greedy to set the search strategy.");
		
		}
		return strategy;
	}

	public static int getMaxRow() {
		return MAX_ROW;
	}

	public static int getMaxCol() {
		return MAX_COL;
	}

	public Goal[][] getGoals(Agent agent) {
		return goalMap.get(agent.getColor());
	}

	public List<Goal> getGoalList() {
		return goalList;
	}

	public boolean[][] getWalls() {
		return walls;
	}
	
	public boolean[][] getTempWalls() {
		return tempWalls;
	}
	
	public void resetTempWalls() {
		tempWalls = new boolean[MAX_ROW][MAX_COL];
	}

	public Box[][] getBoxes() {
		return boxes;
	}

	public Agent[][] getAgents() {
		return agents;
	}

	public Map<Goal, Integer[][]> getDijkstraMap() {
		return this.dijkstraMap;
	}
	
	public LinkedList<Node> search(Strategy strategy, Node initialState) throws IOException {
//		System.err.format("Search starting with strategy %s.\n", strategy.toString());
		
		if(strategy.frontierIsEmpty()) { //first time
			strategy.addToFrontier(initialState);
		}
		
		int iterations = 0;
		while (true) {
			if (iterations == 1000) {
				System.err.println(strategy.searchStatus());
				iterations = 0;
			}
			
			if (strategy.frontierIsEmpty()) {
				return null;
			}
			
			Node leafNode = strategy.getAndRemoveLeaf();
			
			// Goal states
			if (leafNode.isGoalState()) {
				return leafNode.extractPlan();
			}
			
			strategy.addToExplored(leafNode);
			for (Node n : leafNode.getExpandedNodes()) {
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
//					n.calculateDistanceToGoal();
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}

	public static AIClient client;
	private static LinkedList<MultiNode> combinedSolution;
	private static Pos[][] requests;
	private static String[] actions;
	private static Node[] currentStates, backupStates;
	private static MultiNode state;
	private static LinkedList<Agent> agentOrder;
	private static LinkedList<LinkedList<Node>>[]  allSolutions;
	private static LinkedList<Node>[] solutions, originalSolutions;
	private static Strategy[] strategies;
	private static LinkedList<String> actionList;
	private static int reachedAgent;
	
	public static void main(String[] args) throws Exception {
		BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in));

		// Read level and create the initial state of the problem
		client = new AIClient(serverMessages);
		originalSolutions = new LinkedList[agentCounter];
		solutions = new LinkedList[agentCounter];
		allSolutions = new LinkedList[agentCounter];
		strategies = new Strategy[agentCounter];
		agentOrder = new LinkedList<>();
		actionList = new LinkedList<>();
		requests = new Pos[agentCounter][7];
		currentStates = new Node[agentCounter];
		backupStates = new Node[agentCounter];
		
		//Find solutions
		for (Agent a : initialStates.keySet()) {
			Node initialState = initialStates.get(a);
			
			LinkedList<Box> aBoxes = a.getBoxesNotInGoal();
			Box box = aBoxes.isEmpty() ? null : aBoxes.getFirst();
			initialState.goToBox = box;
			initialState.goTo = true;
			
			Strategy strategy = getStrategy("astar", initialState);
			LinkedList<Node> solution = createSolution(strategy, client, initialState);
			strategies[a.getID()] = strategy;
			
			if (solution != null) {
				solutions[a.getID()] = solution;
				originalSolutions[a.getID()] = (LinkedList<Node>) solution.clone();
				updateRequirements(solution, a.getID());
				System.err.println(solution);
//				allSolutions[a.getID()] = new LinkedList<LinkedList<Node>>();
//				allSolutions[a.getID()].add((LinkedList<Node>) solution.clone());
			} else {
				System.err.println("COULD NOT FIND SOLUTION FOR "+a);
			}
		}
		orderAgents();
		System.err.println("ORDER: "+agentOrder);

		for(Agent a : initialStates.keySet()) {
			currentStates[a.getID()] = initialStates.get(a);
		}
		
		int stopped = 0;
		
		while(true) {
			boolean stop = false;
			actions = new String[agentCounter];
			boolean done = true;
			System.err.println("\n---------- NEXT STATE -------------");
			System.err.println("Agent order: " + agentOrder);
			
			reachedAgent = 0;
			while (reachedAgent < agentOrder.size()) {
				int a = agentOrder.get(reachedAgent).getID();
				
				if(actions[a] == null) { //no action yet
					System.err.println("Agent: " + a + " turn at " + reachedAgent + " in order");
		
					// If current agent is done with its current goal
					if((solutions[a] == null || solutions[a].isEmpty())) {
						
						// IF current agent is getting help
						// AND current agent is helping the same agent that helps him
						// AND current agent is allowed to get help
						if(agentIDs[a].getsHelp != null && agentIDs[a].getsHelp == agentIDs[a].isHelping && agentIDs[a].getHelp) {
							System.err.println(a+"STOPPED");	
							stopped++;
							agentIDs[a].isHelping.getHelp = true;
							agentIDs[a].isHelping.isHelping = null;
							agentIDs[a].isHelping.getsHelp = null;
							agentIDs[a].isHelping = null;
							agentIDs[a].getsHelp = null;
							
						// Otherwise, if you are helping another and your goal is done (the goal of helping the other agent)
					    // Release yourself
//						} else if (agentIDs[a].isHelping != null) {
//							agentIDs[a].isHelping = null;
//							agentIDs[a].getHelp = true;
						}
						Node newInitialState = currentStates[a].copy();
						
						if (!newInitialState.isRealGoalState()) {
							
							newInitialState.boxPosition = null;
							newInitialState.requestedPositions = null;
							newInitialState.agentGoal = null;
							
							if (newInitialState.goToBox != null) {
								newInitialState.goTo = !newInitialState.goTo;
							}
							
							if (newInitialState.goTo || newInitialState.goToBox == null) {
								LinkedList<Box> aBoxes = agentIDs[a].getBoxesNotInGoal();
								Box box = aBoxes.isEmpty() ? null : aBoxes.getFirst();
								newInitialState.goToBox = box;
								newInitialState.goTo = true;
							}
														
							if(!newInitialState.isGoalState()) {

								//Create solution to solve own problem
								newInitialState.ignore = true;
	
								solutions[a] = createSolution(getStrategy("astar", newInitialState), client, newInitialState);
								originalSolutions[a] = new LinkedList<Node>(solutions[a]);
								updateRequirements(solutions[a], a);
//								System.err.println(a+"'S OWN NEW SOLUTION \n"+solutions[a]+" from "+newInitialState);
//								System.err.println(a+"'S OWN NEW SOLUTION \n");	
							} else {
								newInitialState.goTo = !newInitialState.goTo;
								
								if (newInitialState.goTo) {
									LinkedList<Box> aBoxes = agentIDs[a].getBoxesNotInGoal();
									Box box = aBoxes.isEmpty() ? null : aBoxes.getFirst();
									newInitialState.goToBox = box;
									newInitialState.goTo = true;
								}
								
								if(!newInitialState.isGoalState()) {
									//Create solution to solve own problem
									newInitialState.ignore = true;
	
									solutions[a] = createSolution(getStrategy("astar", newInitialState), client, newInitialState);
									originalSolutions[a] = new LinkedList<Node>(solutions[a]);
									updateRequirements(solutions[a], a);
//									System.err.println(a+"'S OWN NEW SOLUTION \n"+solutions[a]+" from "+newInitialState);
//									System.err.println(a+"'S OWN NEW SOLUTION \n");	
								}
							}
						}
					}
					
					//Execute
	//				System.err.println("EXECUTE: "+a1+ " \n"+solutions[a1]+ " REQUIRING "+Arrays.toString(requests[a1]));
					actions[a] = getAction(solutions, a, reachedAgent);
					
					//At least one agent has a proper action
					if(actions[a] != "NoOp") {
						done = false;
						updateRequirements(solutions[a], a);
					}
					//Next helper

					System.err.println(Arrays.toString(actions));
					
					if((solutions[a] != null && !solutions[a].isEmpty())) {
						if(agentIDs[a].getHelp) {
							getHelp(reachedAgent, a, solutions[a].get(0));
						} else {
							System.err.println("CANNOT GET HELP");
						}
					}
				}
				reachedAgent++;
			}
			if(stop) {
				break;
			}
			
			//OUT OF INNER LOOP
			boolean execute = true;
			if(done) {
				if(state.isGoalState()) {
					System.err.println("COMPLETED");
					break;
				} else {
					System.err.println("DEADLOCK: ");
					//Replan
					resetAgents();
//					startOver(strategies, agentOrder.get(0));
					execute = false;
//					break;
				}
			}
			
			if(execute){

				// Add state to the combined solution, since the iteration is done
				combinedSolution.add(state);				
				
				// Completed the final goals
				// If so, release any helpers locked to this agent
				for(Agent a : agentIDs) {
					if(currentStates[a.getID()].isRealGoalState()) {
						System.err.println(currentStates[a.getID()]+"is a goal state");
						if(a.getsHelp != null ) { //&& a.getsHelp != a.isHelping) {
							System.err.println(a+" done reset");
							a.getsHelp.getHelp = true;
							a.getsHelp.isHelping = null;
							a.getsHelp = null;
//							stop = true;
						}
						System.err.println(a+" is done");
					}
				}
					
				//Create action string
				String act;
				act = "[";
				for(int i = 0; i<actions.length; i++) {
					act += actions[i];
					if(i < actions.length-1) {
						act += ", ";
					}
				}
				act += "]";
				actionList.add(act);
			
				////////////////TEST
				if(act.equals("[null,null]")) {
					System.err.println("NULL");
					break;
				}

				System.out.println(act);
				System.err.println(act);
				System.err.println(state);
				
				if(stop) {
					break;
				}
//				String response = serverMessages.readLine();
//				 if (response.contains("false")) {
//					 System.err.format("Server responsed with %s to the inapplicable action: %s\n", response, act);
//					 System.err.format("%s was attempted in \n%s\n", act, state.toString());
//					 break;
//				 }
			}
		}
		//OUT OF WHILE LOOP
		
//		System.err.println("OUTSIDE: \n"+combinedSolution);

//		for(String act : actionList) {
//			System.err.println(act);
//			System.out.println(act);
//			String response = serverMessages.readLine();
//			 if (response.contains("false")) {
//				 System.err.format("Server responsed with %s to the inapplicable action: %s\n", response, act);
//				 System.err.format("%s was attempted in \n%s\n", act, state.toString());
//				 break;
//			 }
//		}
		System.err.println("Final solution is of length: "+actionList.size()+" "+stopped);
//		System.err.println(actionList);
	}


	private static void getHelp(int i, int a1, Node node) throws IOException {
		Pos p1 = requests[a1][1];
		Pos p2 = requests[a1][2];
		boolean atOne = true;
		
		Agent inNeed = agentIDs[a1];		
		System.err.println("GET HELP FOR "+a1+"/"+reachedAgent+ " in: \n"+state+p1+ " "+p2);

		//Get helping agent
		Agent helper = null;
		Box b = null;
		String aAction = null;
		if(p1 != null) {
			helper = state.agents[p1.row][p1.col];
			b = state.boxes[p1.row][p1.col];
			if(helper == null && b == null && p2 != null) {
				helper = state.agents[p2.row][p2.col];
				b = state.boxes[p2.row][p2.col];
				atOne = false;
			}

			if(helper == null && b != null && !inNeed.reachableBoxes.contains(b)) {
				for(int j = 0; j < agentOrder.size(); j++) {
					if(agentOrder.get(j).reachableBoxes.contains(b)) {
						helper = agentOrder.get(j);
						aAction = actions[helper.getID()];
						if(aAction == null) {
							break;	
						}
					}
				}
			}
		}
		
//		System.err.println("REQUESTS: "+Arrays.deepToString(requests));
//		System.err.println("ACTIONS: "+Arrays.toString(actions));
//		System.err.println("helper != inNeed: "+(helper != inNeed));
		System.err.println(a1+": helper: "+(helper));
		System.err.println("inNeed.isHelping: "+(inNeed.isHelping));
		if(helper != null) {
			System.err.println("helper.getsHelp: "+helper.getsHelp);
			System.err.println("helper.isHelping: "+helper.isHelping);
		}
		
		// IF helper is not null
		// AND helper is not current agent (inNeed)
		// AND 
		// -- helper is not helping anyone else
		// OR helper is already helping the current agent
		// OR the one the helper is helping has no actions
		// CONCLUSION, the helper can help the current agent
		if (helper != inNeed && helper != null && (helper.isHelping == null || helper.isHelping == inNeed || actions[helper.isHelping.getID()] == null)) { //need/can get help
//			if(actions[helper.getID()] != null && actions[helper.getID()] != "NoOp" && currentStates[helper.getID()].isGoalState()) {
//				System.err.println("return 2");
//				return;
//			}

//			System.err.println(helper.getID()+" can help "+inNeed.getID());
			
			// IF helper is not getting help at all
			// AND current agent is not helping the helper
			// CONCLUSION the helper are not getting help
			if(helper.getsHelp != inNeed && (helper.getsHelp == null || actions[helper.getsHelp.getID()] == null)) {//&& inNeed.isHelping != helper) {
				System.err.println(helper.getID()+" may help "+inNeed.getID());
				if(aAction == null) { //available helper later				
					System.err.println("CASE 1");
					agentOrder.remove(helper);
					agentOrder.add(agentOrder.indexOf(inNeed)+1, helper);
				} else if(helper.getsHelp == null) { //available helper before not getting help
					System.err.println("CASE 2");
					agentOrder.remove(helper);
					agentOrder.add(agentOrder.indexOf(inNeed)+1, helper);
				} else if(inNeed.isHelping == null) { //available helper before getting help while current can be moved
					System.err.println("CASE 3 (MAY REMOVE)");
					agentOrder.remove(inNeed);
					agentOrder.add(agentOrder.indexOf(helper), inNeed);
				}
				updateHelpers();
				planHelp(inNeed, helper, atOne);
				reachedAgent = agentOrder.indexOf(helper)-1;
				
			// If helper is already helping current agent
		    // AND helper cannot get help
			// CONCLUSION they are helping each other
			} else if (helper.getsHelp == inNeed && !helper.getHelp) {//Mutual help
				helper.isHelping = inNeed;
				inNeed.getsHelp = helper;
				agentOrder.remove(helper);
				agentOrder.add(agentOrder.indexOf(inNeed)+1, helper);
				System.err.println("CASE LOOP");
				
				updateHelpers();
				planHelp(inNeed, helper, atOne);
				reachedAgent = agentOrder.indexOf(helper)-1;
			}
		}
	}


	private static boolean oneway(Pos p) {
		int r = p.row;
		int c = p.col;
		int count = 4;
		if (r-1>0 && !walls[r-1][c]) count--;
		if (r+1<getMaxRow() && !walls[r+1][c]) count--;
		if (c-1>0 && !walls[r][c-1]) count--;
		if (c+1<getMaxCol() && !walls[r][c+1]) count--;
		return count >= 2;
	}

	private static void planHelp(Agent inNeed, Agent helper, boolean atOne) throws IOException {
		int a1 = inNeed.getID();
		
		System.err.println("inNeed: "+inNeed+" helper:"+helper);
		LinkedList<Pos> positions = new LinkedList<>() ;
		positions.add(requests[a1][0]); //required in previous state
		positions.add(currentStates[a1].getRequired());//required in current state
		positions.add(requests[a1][1]); //p, required in next state
		positions.add(requests[a1][2]); //p2, required in after next state		
		positions.add(requests[a1][3]); //required after after next state
		positions.add(requests[a1][4]); //required after after after next state
		positions.add(requests[a1][5]); //required after after after after next state
		positions.add(requests[a1][6]); //required after after after after after next state

		System.err.println(helper+" POSITIONS: "+positions);
		

		//Reverse action if getting too close
		if(atOne) {
			String act = actions[helper.getID()];
			if(act != null && !act.equals("NoOp")) {
				System.err.println("REVERSE");
	//			System.err.println("before reverse: "+state+ " "+currentStates[helper.getID()]);
				Command reverseAction = Command.reverse(act);
				state = new MultiNode(state, helper.getID(), reverseAction);
				currentStates[helper.getID()] = backupStates[helper.getID()];
	//			System.err.println("backup state:"+currentStates[helper.getID()]);
	//			System.err.println("reversing "+act+" to "+reverseAction);
	//			System.err.println("after reverse: "+state+ " "+currentStates[helper.getID()]);
			}
			actions[helper.getID()] = null;
		}
		
		//Replan
		if(helper.isHelping != null) {
			helper.isHelping.getsHelp = null;
		}

		helper.isHelping = inNeed;
		inNeed.getsHelp = helper;
		
		Node newInitialState = currentStates[helper.getID()].copy();
		newInitialState.ignore = false;
		newInitialState.help = inNeed;
		newInitialState.requestedPositions = positions;
		newInitialState.goToBox = null;
		LinkedList<Node> sol = createSolution(getStrategy("bfs", newInitialState), client, newInitialState);

		if(sol == null) {
			Node curState = null;
			System.err.println("REVERSE AND PLAN");
			String act = actions[inNeed.getID()];
			if(act != null && !act.equals("NoOp")) {
				System.err.println("REVERSE");
				Command reverseAction = Command.reverse(act);
				state = new MultiNode(state, inNeed.getID(), reverseAction);
				curState = currentStates[inNeed.getID()];
				currentStates[inNeed.getID()] = backupStates[inNeed.getID()];
				
				actions[inNeed.getID()] = "NoOp";
				solutions[inNeed.getID()].add(0,curState);
			}	
			sol = createSolution(getStrategy("bfs", newInitialState), client, newInitialState);
//			if(sol != null) {
				
//			}
		}
		if(sol == null) {
			System.err.println("REPLAN MUTUAL HELP");
			positions = new LinkedList<>();
			for(int i = 0; i < solutions[inNeed.getID()].size(); i++) {
				Node n = solutions[inNeed.getID()].get(i);
				System.err.println(solutions[inNeed.getID()]);
				if(oneway(n.getRequired())) {
					if (!positions.contains(n.getRequired())) positions.add(n.getRequired());
					
//					Pos p = new Pos(n.agentRow, n.agentCol);
//					if (!positions.contains(p)) positions.add(p);
				} else {
					break;
				}
			}
			System.err.println(helper+" FIRST PART OF AVOIDLIST: "+positions);
			Pos pOld = null;
			if(positions.size() > 1) {
				pOld = positions.get(1);
			}
			Pos p = positions.getFirst();
			
			while(true) {
				int r = p.row;
				int c = p.col;
				System.err.println(p+", "+pOld);
				if (r-1>0 && !walls[r-1][c] && !(pOld.row == r-1 && pOld.col == c)) {
					pOld = new Pos(r,c);
					p = new Pos(r-1, c);
				}
				else if (r+1<getMaxRow() && !walls[r+1][c] && !(pOld.row == r+1 && pOld.col == c)) {
					pOld = new Pos(r,c);
					p = new Pos(r+1, c);
				}
				else if (c-1>0 && !walls[r][c-1] && !(pOld.row == r && pOld.col == c-1)) {
					pOld = new Pos(r,c);
					p = new Pos(r, c-1);
				}
				else if (c+1<getMaxCol() && !walls[r][c+1] && !(pOld.row == r && pOld.col == c+1)) {
					pOld = new Pos(r,c);
					p = new Pos(r, c+1);
				} else {
					pOld = new Pos(r,c);
				}
				if (!p.equals(pOld) && oneway(p)) {
					positions.add(p);
				} else {
					break;
				}
			}
			
			inNeed.getHelp = false;
			helper.getHelp = true;

			System.err.println(helper+" AVOIDLIST: "+positions);
			System.err.println("Helper: " + helper);
			System.err.println("Helper.getsHelp: " + helper.getsHelp);
			System.err.println("Helper.isHelping: " + helper.isHelping);
			System.err.println("InNeed: " + inNeed);
			System.err.println("InNeed.getsHelp: " + inNeed.getsHelp);
			System.err.println("InNeed.isHelping: " + inNeed.isHelping);
			
			newInitialState.ignore = true;
			newInitialState.requestedPositions = positions;
			sol = createSolution(getStrategy("astar", newInitialState), client, newInitialState);
		}
		
		
		solutions[helper.getID()] = sol;
		updateRequirements(solutions[helper.getID()], helper.getID());
		System.err.println(helper+"'S HELP SOL2 \n"+solutions[helper.getID()]+" from "+newInitialState);
	}

	private static void resetAgents() {
		System.err.println("--------- RESET SOLUTIONS ----------- ");
		
		for (int i = 0; i < agentIDs.length; i++) {
			Agent a = agentIDs[i];
			
			a.getHelp = true;
			a.getsHelp = null;
			a.isHelping = null;
			
			solutions[a.getID()] = null;
			actions[a.getID()] = null;
			
		}
	}
	
	private static void startOver(Strategy[] strategies, Agent agent) throws IOException {
		System.err.println("STARTING OVERRR");
		actionList = new LinkedList<>();
		combinedSolution = new LinkedList<>();
		state = new MultiNode(client, boxes, agents);
		combinedSolution.add(state);

		//Try new solution for a
		LinkedList<Node> solution = createSolution(strategies[agent.getID()], client, initialStates.get(agent));
		if(solution != null && !solution.isEmpty()) {
			solutions[agent.getID()] = solution;
//			System.err.println(agent.getID()+"'s BACKUP PLAN: \n"+solution);
			allSolutions[agent.getID()].add((LinkedList<Node>) solution.clone());
			orderAgents();
		}
		
		//Combine with other solutions
		for(Agent a : agentOrder) {
			if(a != agent) {
				solutions[a.getID()] = allSolutions[a.getID()].get(0); //TEMP
			}
		}
	}

	private static void orderAgents() {		
		agentOrder = new LinkedList<>();
		Agent a = null;
		for(int i = 0; i < agentCounter; i++) {
			a = agentIDs[i];
			LinkedList<Node> solution = solutions[i];

			//System.err.println("COST OF "+a.getID()+" :"+(solution.getLast().h()+solution.getLast().g()));
			int index = 0;
			while(index<agentOrder.size()) {
				int agentID = agentOrder.get(index).getID();
				int cost = 0;
				if(solutions[agentID] != null && !solutions[agentID].isEmpty()) {
					cost = solutions[agentID].getLast().h();
				}
				if(solution != null && !solution.isEmpty() && solution.getLast().h() <= cost) {
					break;
				}
				index++;
			}
			agentOrder.add(index, a);

			System.err.println("ADDED: "+a +" at "+index+": "+agentOrder);
		}
		//Set helpers
		updateHelpers();
	}


	private static void updateHelpers() {
		for(int i = 0; i<agentOrder.size()-1; i++) {
			agentOrder.get(i).setHelper(agentOrder.get(i+1));
		}
		agentOrder.getLast().setHelper(null);
	}

	private static String getAction(LinkedList<Node>[] solutions, int a, int i) throws Exception {
		if (solutions[a] != null && !solutions[a].isEmpty()) {
			Node node = solutions[a].get(0);
			Pos p = node.getRequired();

			if ((!state.isEmpty(p) && state.agents[p.row][p.col] != node.getAgent()) // conflict with other agents in current state
					|| !combinedSolution.getLast().isEmpty(p)) { // conflict with beginning of state

//				System.err.println("WANT: "+p);
//				System.err.println("Same: "+state);
//				System.err.println("Prev: "+(combinedSolution.getLast()));
				System.err.println("NO OP 1: "+node.action.toString());
				
				return "NoOp";	
			}
			
			//Conflict with higher-ranked agents
//			for(int j = 0; j < i; j++) { //Higher-order agents
//				int a2 = agentOrder.get(j).getID();
//				Pos pos = requests[a2][1];
//				if (pos != null && node != null && node.getRequired().equals(pos)) {
//					System.err.println("NO OP 2: "+node.action.toString());
//					return "NoOp";
//				}
//			}
			
			//Can execute
			state = new MultiNode(state, a, node.action);
			
			node.action.toString();
			requests[a][0] = currentStates[a].getRequired();
//			System.err.println(requests[a][0]+ " req in "+currentStates[a]);
			backupStates[a] = currentStates[a]; 
			currentStates[a] = node;
//			System.err.println("new current state:"+currentStates[a]);
			solutions[a].remove(0);
			return node.action.toString();
		}
		System.err.println(a+": NO OP 3");
		return "NoOp";
	}
	
	private static void updateRequirements(LinkedList<Node> solution, int a) {
		if(solution != null) {
//			for(int j = 1; j < requests.length; j++) {
//				requests[a][j] = null;	
//			}
			requests[a][1] = null;
			requests[a][2] = null;
			requests[a][3] = null;
			requests[a][4] = null;
			requests[a][5] = null;
			requests[a][6] = null;
//			int i = 1;
//			while(solution.size() > i && requests.length > i) {
//				requests[a][i] = solution.get(i-1).getRequired();
//				i++;
//			}
			if (!solution.isEmpty()) {
				requests[a][1] = solution.get(0).getRequired();
				if (solution.size() > 1) {
					requests[a][2] = solution.get(1).getRequired();
					if (solution.size() > 2) {
						requests[a][3] = solution.get(2).getRequired();
						if (solution.size() > 3) {
							requests[a][4] = solution.get(3).getRequired();
							if (solution.size() > 4) {
								requests[a][5] = solution.get(4).getRequired();
								if (solution.size() > 5) {
									requests[a][6] = solution.get(5).getRequired();
								}
							}
						}
					}
				}
			}
		}
	}
	
	private static LinkedList<Node> createSolution(Strategy strategy, AIClient client, Node initialState)
			throws IOException {
		LinkedList<Node> solution;
		try {
			solution = client.search(strategy, initialState);
		} catch (OutOfMemoryError ex) {
			System.err.println("Maximum memory usage exceeded.");
			solution = null;
		}
		return solution;
	}

	public String getColor(char g) {
		return goalColor.get(g);
	}

	public Goal[][] getGoals() {
		return goals;
	}

	public int getAgentNum() {
		return agentCounter;
	}

	public MultiNode getCurrentState() {
		return combinedSolution.getLast();
	}

	public MultiNode getCurrentSubState() {
		return state;
	}
}











