package statespace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import statespace.Command;
import statespace.Command.Type;

public class Node {
	private static final Random RND = new Random(1);

	public int agentRow;
	public int agentCol;
	
	// Arrays are indexed from the top-left of the level, with first index being row and second being column.
	// Row 0: (0,0) (0,1) (0,2) (0,3) ...
	// Row 1: (1,0) (1,1) (1,2) (1,3) ...
	// Row 2: (2,0) (2,1) (2,2) (2,3) ...
	// ...
	// (Start in the top left corner, first go down, then go right)
	// E.g. this.walls[2] is an array of booleans having size MAX_COL.
	// this.walls[row][col] is true if there's a wall at (row, col)
	//

	public Box[][] boxes;
	public Goal[][] goals;
	
	public Pos agentGoal;
	public ArrayList<Pos> requestedPositions; 
	
	public Node parent;
	public Command action;
	
	private int g;
	private int h;
	private AIClient client;
	
	private int _hash = 0;

	private Agent agent;

	private Pos required;

	public boolean ignore = true;
	public Agent help = null;

	public Pos boxPosition;

	public Box goToBox;
	public Goal goToGoal;
	
	public boolean goTo;
	
	public Node(Node parent, AIClient client, Agent agent, Pos agentPos, Command action) {
		this.parent = parent;
		this.client = client;
		this.agent = agent;
		this.agentRow = agentPos.row;
		this.agentCol = agentPos.col;
		this.action = action;
		this.goals = new Goal[client.getMaxRow()][client.getMaxCol()];
		this.boxes = new Box[client.getMaxRow()][client.getMaxCol()];
	}
	
	public int g() {
		return this.g;
	}
	
	public int h() {
		if (this.h == 0) this.h = this.calculateDistanceToGoal();
		return this.h;
	}

	public boolean isInitialState() {
		return this.parent == null;
	}
	
	public boolean isGoalState() {

		if (boxPosition != null){
			return boxes[boxPosition.row][boxPosition.col] == null;
		}

		// Check for positions, if another agents has requested something
		if (requestedPositions != null) {
			boolean empty = true;
			for(Pos p : requestedPositions) {
				if (p != null && !isEmpty(p)) {
					empty = false;
					break;
				}
			}
			
			return empty; 
		}
		
		// Check if agents is placed correct, if he is trying to find a solutions to a conflict, without asking for help
		if (agentGoal != null && (agentGoal.row != agentRow || agentGoal.col != agentCol)) return false; 
		
		if (goToBox == null) return isRealGoalState();
		
		return isIntermediateGoalState();
	}
	
	public boolean isIntermediateGoalState() {

		if (goToBox == null) return false;
		
		boolean success = false;
		
		if(goTo) {
			
			if (agentRow > 0)
				success = goToBox == boxes[agentRow-1][agentCol];
			
			if (!success && agentRow < client.getMaxRow()-1) 
				success = goToBox == boxes[agentRow+1][agentCol];
			
			if (!success && agentCol > 0)
				success = goToBox == boxes[agentRow][agentCol-1];
			
			if (!success && agentCol < client.getMaxCol()-1)
				success = goToBox == boxes[agentRow][agentCol+1];
			
		} else {
			
			for (int row = 1; row < client.getMaxRow() - 1; row++) {
				for (int col = 1; col < client.getMaxCol() - 1; col++) {
					if (boxes[row][col] == goToBox) {
						
						if (goToGoal.getPos().row == row && goToGoal.getPos().col == col){
							goToBox.goal = goToGoal;
							
							return true;
						}
					}
				}
			}
		}
		return success;
	}
	
	public boolean isRealGoalState() {
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				char g = goals[row][col] != null ? goals[row][col].getLabel() : 0;
				char b = boxes[row][col] != null ? Character.toLowerCase(boxes[row][col].getLabel()) : 0;
				if (g > 0 && b != g && client.getColor(g) != agent.getColor()) {
					return false;
				}
			}
		}
		return true;
	}
	public ArrayList<Node> getExpandedNodes() {
		ArrayList<Node> expandedNodes = new ArrayList<Node>(Command.EVERY.length);
		for (Command c : Command.EVERY) {
			
			// Determine applicability of action
			int newAgentRow = this.agentRow + Command.dirToRowChange(c.dir1);
			int newAgentCol = this.agentCol + Command.dirToColChange(c.dir1);
			
			if (c.actionType == Type.Move) {
				
				// Check if there's a wall or box on the cell to which the agent is moving
				if (this.cellIsFree(newAgentRow, newAgentCol)) {
					Node n = this.ChildNode(newAgentRow, newAgentCol, c);

					n.required = new Pos(newAgentRow, newAgentCol);
					expandedNodes.add(n);
				}
			} else if (c.actionType == Type.Push) {
				// Make sure that there's actually a box to move
				
				if (this.boxAt(newAgentRow, newAgentCol)) {
					int newBoxRow = newAgentRow + Command.dirToRowChange(c.dir2);
					int newBoxCol = newAgentCol + Command.dirToColChange(c.dir2);
					
					boolean notAllowed = false;
					for (Box box : agent.getReachableBoxes())
						if (box.inWorkingProcess && box == this.boxes[newAgentRow][newAgentCol] && box != goToBox)
							notAllowed = true;

					// Check that new cell of box is free
					if (this.cellIsFree(newBoxRow, newBoxCol) && !notAllowed) {
						Node n = this.ChildNode(newAgentRow, newAgentCol, c);
						
						n.boxes[newBoxRow][newBoxCol] = this.boxes[newAgentRow][newAgentCol];
						n.boxes[newAgentRow][newAgentCol] = null;
						n.required = new Pos(newBoxRow, newBoxCol);
						expandedNodes.add(n);
					}
				}
			} else if (c.actionType == Type.Pull) {
				
				// Check that new cell of is free
				if (this.cellIsFree(newAgentRow, newAgentCol)) {
					int boxRow = this.agentRow + Command.dirToRowChange(c.dir2);
					int boxCol = this.agentCol + Command.dirToColChange(c.dir2);
					
					boolean notAllowed = false;
					for (Box box : agent.getReachableBoxes())
						if (box.inWorkingProcess && box == this.boxes[boxRow][boxCol] && box != goToBox)
							notAllowed = true;
					
					// Make sure that there's actually a box to move
					if (this.boxAt(boxRow, boxCol) && !notAllowed) {
						Node n = this.ChildNode(newAgentRow, newAgentCol, c);
						
						n.boxes[this.agentRow][this.agentCol] = this.boxes[boxRow][boxCol];
						n.boxes[boxRow][boxCol] = null;
						n.required = new Pos(newAgentRow, newAgentCol);
						
						expandedNodes.add(n);
					}
				}
			}
		}
		Collections.shuffle(expandedNodes, RND);
		return expandedNodes;
	}

	public boolean cellIsFree(int row, int col) {
		boolean helping = true;
		if(!ignore && agent.isHelping != null) {
			helping = client.getCurrentSubState().agents[row][col] != agent.isHelping
					&& client.getCurrentState().agents[row][col] != agent.isHelping
					&& !agent.isHelping.getReachableBoxes().contains(client.getCurrentSubState().boxes[row][col])
					&& !agent.isHelping.getReachableBoxes().contains(client.getCurrentState().boxes[row][col]);
		}
		
		Box b = null;
		if (agent.isHelping != null) {
			b = client.getCurrentState(agent.isHelping.getID()).goToBox;
		}
		
		boolean allowed = false;
		for (Box box : agent.getReachableBoxes())
			if (box.inWorkingProcess && box == boxes[row][col] && box != goToBox)
				allowed = true;
		
		
		return !client.getWalls()[row][col] && (boxes[row][col] == null || allowed) && helping;
	}
	
	public boolean isEmpty(Pos pos) {
		Node node = null;
		if (agent.isHelping != null) {
			node = client.getCurrentState(agent.isHelping.getID());	
		}
		return (boxes[pos.row][pos.col] == null || node == null || boxes[pos.row][pos.col] == node.goToBox) && !(agentRow == pos.row && agentCol == pos.col);
	}
	private boolean boxAt(int row, int col) {
		return this.boxes[row][col] != null;
	}

	private Node ChildNode(int r, int c, Command action) {
		Node copy = new Node(this, client, agent, new Pos(r, c), action);
		copy.goals = goals;
		copy.ignore = ignore;
		copy.help = help;
		copy.boxPosition = boxPosition;
		copy.requestedPositions = requestedPositions;
		copy.goTo = goTo;
		copy.goToBox = goToBox;
		copy.goToGoal = goToGoal;
		copy.agentGoal = agentGoal;
		copy.g = g + 1;


		for (int row = 0; row < client.getMaxRow(); row++) {
			System.arraycopy(this.boxes[row], 0, copy.boxes[row], 0, client.getMaxCol());
		}
		return copy;
	}

	public ArrayList<Node> extractPlan() {
		ArrayList<Node> plan = new ArrayList<Node>();
		Node n = this;
		while (!n.isInitialState()) {
			plan.add(0,n);
			n = n.parent;
		}
		return plan;
	}

	@Override
	public int hashCode() {
		if (this._hash == 0) {
			final int prime = 31;
			int result = 1;
			result = prime * result + this.agentCol;
			result = prime * result + this.agentRow;
			result = prime * result + Arrays.deepHashCode(this.boxes);
			this._hash = result;
		}
		return this._hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		Node other = (Node) obj;
		if (this.agentRow != other.agentRow || this.agentCol != other.agentCol)
			return false;
		if (!Arrays.deepEquals(this.boxes, other.boxes))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (int row = 0; row < client.getMaxRow(); row++) {

			for (int col = 0; col < client.getMaxCol(); col++) {
				if (agentGoal != null && agentGoal.row == row && agentGoal.col == col) {
					s.append("L");
				} else if (client.getWalls()[row][col]) {
					s.append("+");
				} else if (client.getTempWalls()[row][col]) {
					s.append("+");
				} else if (this.boxes[row][col] != null) {
					s.append(this.boxes[row][col]);
				} else if ((goals[row][col] != null ? goals[row][col].getLabel() : 0) > 0) {
					s.append(goals[row][col]);
				} else if (row == this.agentRow && col == this.agentCol) {
					s.append(agent.getLabel());
				} else {
					s.append(" ");
				}
			}
			s.append("\n");
		}
		s.append("Cost of step: g = " + g + ", h = " + h);
		
		return s.toString();
	}
	
	public int calculateDistanceToGoal() {
		// Multiply distance to box or goal by this factor 
		int factor = 2;
		
		int penalty = 0;
		
		// If helping
		Box requestedBox = null;
		int requestedBoxH = Integer.MAX_VALUE;
		if (requestedPositions != null) {
			for (Pos p : requestedPositions) {
				if (p != null) {
					requestedBox = boxes[p.row][p.col];
					
					if (requestedBox != null &&requestedBox.getColor() == agent.getColor()) {
						requestedBoxH = Math.abs(p.row - agentRow) + Math.abs(p.col - agentCol) * factor; 
						break;
					}
				}
			}
		}
		
		// Don't move over another agent, another agents box or another agents goal
		if(required != null) {
			
			Box box = client.getCurrentSubState().boxes[required.row][required.col];
			Agent agent = client.getCurrentSubState().agents[required.row][required.col];
			Goal goal = client.getGoals()[required.row][required.col];
			
			// If in an alley, no penalty is added
			if (!(goToGoal != null && AIClient.oneway(required))) {

				// If you try to go through some box - that is not your own
				if (box != null && !box.getColor().equals(this.agent.getColor()))
					penalty += 8;
				
				// If you try to go through another agent
				if (agent != null && agent != this.agent)
					penalty += 5;
				
				// If you try to go through another agents goal
				if (goal != null && goals[required.row][required.col] == null)
					penalty += 5;
			}
			
			Pos aPos = new Pos(agentRow, agentCol);
			
			// If agent position differ from required position (i.e. pushing)
			// And you are not in an alley
			if (!aPos.equals(required) && !(goToGoal != null && AIClient.oneway(aPos))) {					
				
				Box aBox = client.getCurrentSubState().boxes[agentRow][agentCol];
				Agent aAgent = client.getCurrentSubState().agents[agentRow][agentCol];
				Goal aGoal = client.getGoals()[agentRow][agentCol];
				
				// If you try to go through some box - that is not your own
				if (aBox != null && !aBox.getColor().equals(this.agent.getColor()))
					penalty += 5;
				
				// If you try to go through another agent
				if (aAgent != null && aAgent != this.agent)
					penalty += 5;
				
				// If you try to go through another agents goal
				if (aGoal != null && goals[agentRow][agentCol] == null)
					penalty += 5;
			}
		}
		
		// Add penalty moving a box out of its goal
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				
				Box box = boxes[row][col];
				Goal goal = goals[row][col];
				
				// If box is in its goal, subtract 5
				if (AIClient.agentCounter == 1 && box != null && box.goal != null && box.goal.equals(goal))
					penalty -= 1000;
				else if (box != null && box.goal != null && box.goal.equals(goal))
					penalty -= 5;
			}
		}
		
		// Try to make the agent push a box instead of pulling
		int distanceForBox = 0;
		int distanceForAgent = 0;
		if (goToBox != null && goToGoal != null) {
			for (int row = 1; row < client.getMaxRow() - 1; row++) {
				for (int col = 1; col < client.getMaxCol() - 1; col++) {
					if (goToBox.equals(boxes[row][col])) {
						distanceForBox = client.getDijkstraMap().get(goToGoal)[row][col];
					}
				}
			}
			distanceForAgent = client.getDijkstraMap().get(goToGoal)[agentRow][agentCol];
			
			if (distanceForAgent < distanceForBox)
				penalty += 8;
		}
		
		// If the agent is doing his own shit
		if (goToBox != null) {
			
			// If the agent is going for a box  
			if (goTo) {	
				return goToBox.getDijkstra()[agentRow][agentCol] * factor + penalty;

			// If the agent is moving a box to its goal
			} else {
				for (int row = 1; row < client.getMaxRow() - 1; row++) {
					for (int col = 1; col < client.getMaxCol() - 1; col++) {
						if (boxes[row][col] == goToBox) {
							return client.getDijkstraMap().get(goToGoal)[row][col] * factor + penalty;
						}
					}
				}
			}
		}
		
		// If helping another agent and have to move some box
		if (requestedBox != null)
			return requestedBoxH + penalty;
		
		return penalty;
	}

	public Pos getRequired() {
		return required;
	}

	public Node copy() {
		Node n = new Node(null, client, agent, new Pos(agentRow, agentCol), action);

		
		for (int row = 0; row < client.getMaxRow(); row++) {
			System.arraycopy(boxes[row], 0, n.boxes[row], 0, client.getMaxCol());
		}
		
		n.required = required;
		n.agentRow = agentRow;
		n.agentCol = agentCol;
		n.action = action;
		n.goals = goals;
		n.goTo = goTo;
		n.goToBox = goToBox;
		n.goToGoal = goToGoal;
		return n;
	}

	public Agent getAgent() {
		return agent;
	}
	
	public void updateBoxes() {
		boxes = new Box[client.getMaxRow()][client.getMaxCol()];
		for (int row = 0; row < client.getMaxRow(); row++) {
			for (int col = 0; col < client.getMaxCol(); col++) {
				Box b = client.getCurrentSubState().boxes[row][col];

				if(b != null && agent.getReachableBoxes().contains(b)) {
					boxes[row][col] = b;
					b.updatePos(row,col);
				}
			}	
		}
		
		for (Box b : this.agent.reachableBoxes)
			if (b.goal != null)
				if (!b.getPos().equals(b.goal.getPos()))
					b.goal = null;

	}
}