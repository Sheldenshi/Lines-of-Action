/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;

import java.util.List;


import static loa.Piece.*;

/** An automated Player.
 *  @author Shelden Shi
 */
class MachinePlayer extends Player {

    /** A position-score magnitude indicating a win (for white if positive,
     *  black if negative). */
    private static final int WINNING_VALUE = Integer.MAX_VALUE - 20;
    /** A magnitude greater than a normal value. */
    private static final int INFTY = Integer.MAX_VALUE;

    /** A new MachinePlayer with no piece or controller (intended to produce
     *  a template). */
    MachinePlayer() {
        this(null, null);
    }

    /** A MachinePlayer that plays the SIDE pieces in GAME. */
    MachinePlayer(Piece side, Game game) {
        super(side, game);
    }

    @Override
    String getMove() {
        Move choice;
        assert side() == getGame().getBoard().turn();
        choice = searchForMove();
        getGame().reportMove(choice);
        return choice.toString();
    }

    @Override
    Player create(Piece piece, Game game) {
        return new MachinePlayer(piece, game);
    }

    @Override
    boolean isManual() {
        return false;
    }

    /** Return a move after searching the game tree to DEPTH>0 moves
     *  from the current position. Assumes the game is not over. */
    private Move searchForMove() {
        Board work = new Board(getBoard());
        assert side() == work.turn();
        _foundMove = null;
        if (side() == WP) {
            findMove(work, chooseDepth(), true, 1, -INFTY, INFTY);
        } else {
            findMove(work, chooseDepth(), true, -1, -INFTY, INFTY);

        }
        return _foundMove;
    }

    /** Find a move from position BOARD and return its value, recording
     *  the move found in _foundMove iff SAVEMOVE. The move
     *  should have maximal value or have value > BETA if SENSE==1,
     *  and minimal value or value < ALPHA if SENSE==-1. Searches up to
     *  DEPTH levels.  Searching at level 0 simply returns a static estimate
     *  of the board value and does not set _foundMove. If the game is over
     *  on BOARD, does not set _foundMove. */
    private int findMove(Board board, int depth, boolean saveMove,
                         int sense, int alpha, int beta) {
        if (board.winner() != null && board.winner() != EMP) {
            if (board.winner() == WP) {
                return WINNING_VALUE + depth;
            } else if (board.winner() == BP) {
                return -WINNING_VALUE - depth;
            }
        } else if (depth == 0) {
            return normalEva(board);
        }
        if (sense == 1) {
            return findMoveWP(board, depth, saveMove, sense, alpha, beta);
        } else {
            return findMoveBP(board, depth, saveMove, sense, alpha, beta);
        }
    }

    /** Return Find moves for white piece.
     * @param board board
     * @param depth depth
     * @param saveMove ifsavemove
     * @param sense sense
     * @param alpha alpha
     * @param beta beta*/
    private  int findMoveWP(Board board, int depth, boolean saveMove,
                            int sense, int alpha, int beta) {
        int bestSoFar = -INFTY;
        Board updated = new Board(board);
        List<Move> movesW = updated.legalMoves();
        for (Move m : movesW) {
            if (updated.isLegal(m)) {
                updated.makeMove(m);
                int r = findMove(updated, depth - 1,
                        false, -sense, alpha, beta);
                updated.retract();
                if (r >= bestSoFar) {
                    bestSoFar = r;
                    alpha = Math.max(alpha, r);
                    if (saveMove) {
                        _foundMove = m;
                    }
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
        }
        return bestSoFar;
    }

    /** Return find moves for black piece.
     *  @param board board
     *  @param depth depth
     *  @param saveMove ifsavemove
     *  @param sense sense
     *  @param alpha alpha
     *  @param beta beta*/
    private int findMoveBP(Board board, int depth, boolean saveMove,
                           int sense, int alpha, int beta) {
        int bestSoFar = INFTY;
        Board updated = new Board(board);
        List<Move> movesB = updated.legalMoves();
        for (Move m : movesB) {
            if (updated.isLegal(m)) {
                updated.makeMove(m);
                int r = findMove(updated, depth - 1,
                        false, -sense, alpha, beta);
                updated.retract();
                if (r <= bestSoFar) {
                    bestSoFar = r;
                    beta = Math.min(beta, r);
                    if (saveMove) {
                        _foundMove = m;
                    }
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
        }
        return bestSoFar;
    }

    /** Return a search depth for the current position. */
    private int chooseDepth() {
        return 4;
    }

    /** Return an value between -10000 and 10000.
     * @param board board */
    private int normalEva(Board board) {
        int score  = 0;

        int[] optionsAndBlocked = board.isBlocked();
        score += optionsHelper(optionsAndBlocked);
        if (board.movesMade() > 10) {
            List<Integer> white = board.getRegionSizes(WP);
            int whiteLen = white.size();
            int whiteGreatest = white.get(0);
            List<Integer> black = board.getRegionSizes(BP);
            int blackLen = black.size();
            int blackGreatest = black.get(0);
            score += (blackLen - whiteLen) * level1;
        }

        if (board.turn().opposite() == WP) {
            score += centerMassHelper(board, WP);
            if (board.getAtCornerWP() > 0) {
                score -= level3;
            }
            if (board.getLastTwoMoves()[1].isCapture()) {
                score += level0;
            }
        } else {
            score += centerMassHelper(board, BP);
            if (board.getAtCornerWP() > 0) {
                score += level3;
            }
            if (board.getLastTwoMoves()[1].isCapture()) {
                score -= level0;
            }
        }
        return score;
    }

    /** Returns the board estimate based on
     * number of move options and blocking information.
     * @param info info*/
    private int optionsHelper(int[] info) {
        int score = 0;
        int optionsWP = info[2];
        int optionsBP = info[3];
        int difference = 10;
        if (optionsWP - optionsBP > difference) {
            score += level0;
        } else if (optionsBP - optionsWP > difference) {
            score -= level0;
        }
        return score;
    }

    /** Returns the score based on avg dis to CM.
     * max distance is 3.0 and goal is close to 1.0
     * awards a shorter distance.
     * awards a center of mass that is close to
     * the middle of the board.
     * @param board board
     * @param side side*/
    private int centerMassHelper(Board board, Piece side) {
        int score = 0;
        final double maxDis = 3.0;
        double avgDisWP = board.getAvgDisWP();
        double avgDisBP = board.getAvgDisBP();
        final double goalDis = 1.5;
        int minDisToCenterWP = Math.min(board.getCentermassWP().
                        distance(Square.sq("d4")),
                board.getCentermassWP().distance(Square.sq("e5")));
        int minDisToCenterBP = Math.min(board.getCentermassBP().
                        distance(Square.sq("d4")),
                board.getCentermassBP().distance(Square.sq("e5")));
        if (side == WP) {
            score += (maxDis - avgDisWP) * level2;
            if (minDisToCenterWP < goalDis) {
                score += level0;
            }

        } else {
            score -= (maxDis - avgDisBP) * level2;
            if (minDisToCenterBP < goalDis) {
                score -= level0;
            }
        }
        return score;
    }

    /** Used to convey moves discovered by findMove. */
    private Move _foundMove;

    /** importance level of. */
    private final int level5 = 10000;
    /** importance level of. */
    private final int level4 = 8000;
    /** importance level of. */
    private final int level3 = 6000;
    /** importance level of. */
    private final int level2 = 4000;
    /** importance level of. */
    private final int level1 = 2000;
    /** importance level of. */
    private final int level0 = 1000;

}
