package com.example.tetris.view;

import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tetris.FileControl;
import com.example.tetris.Main;
import com.example.tetris.MaxScore;
import com.example.tetris.R;
import com.example.tetris.Start;
import com.example.tetris.model.BlockUnit;
import com.example.tetris.model.TetrisBlock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Created by Administrator on 2017/7/21.
 */

public class TetrisView extends View {

    /*
     * 主画板，用于显示游戏主要部分，即活动部分，俄罗斯方块下降，消去等
     *
     */

    boolean flag;

    public final static int CLEAR = 1;

    public static int difficultyType = 0;//难度类型

    public static int beginPoint = 10;  //网格开始坐标值，横纵坐标的开始值都是此值

    public static int max_x, max_y;     //保存俄罗斯方块单元的最大横纵坐标

    public static float beginX;         //俄罗斯方块初始x坐标

    public int dropSpeed = 300;         //俄罗斯方块下落线程默认休眠时间

    public int currentSpeed = 300;      //俄罗斯方块下落线程当前休眠时间

    public boolean isRun = true;        //标识游戏是否正在进行

    public boolean canMove = false;     //标识此时俄罗斯方块是否能左右移动

    public Thread dropThread;          //游戏主线程

    private int[] map = new int[100];   //保存每行网格中包含俄罗斯方块单元的个数

    private Main father;                //调用此对象的Activity对象

    private TetrisBlock fallingBlock;   //正在下落的俄罗斯方块

    public Thread thread = new Thread();//俄罗斯方块下落线程

    private float x1, y1, x2, y2;       //保存onTouchEvent中的起始坐标和结束坐标

    private ArrayList<TetrisBlock> blocks = new ArrayList<>();

    private float h, w;//保存TetrisView的宽和高

    public TetrisView(Context context) {
        super(context);
    }

    public TetrisView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void clear() {
        //清空游戏状态
        isRun = false;
        blocks.clear();
        thread = new Thread();
        fallingBlock = null;
        flag = true;
        //重置初始得分，等级和速度
        father.scoreValue = 0;
        father.levelValue = father.speedValue = (difficultyType - 1) * 10;
        father.runOnUiThread(new Runnable() {
            @Override
            //更新UI
            public void run() {
                Message msg = new Message();
                msg.what = CLEAR;
                father.handler.sendMessage(msg);
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        /*
         * 触摸事件，根据滑动方向左右移动俄罗斯方块，向上滑动则旋转俄罗斯方块
         */

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            //记录触摸滑动事件起始位置坐标
            x1 = event.getX();
            y1 = event.getY();
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            //记录触摸庝事件结束位置坐标

            //若此时俄罗斯方块不能左右移动，即不在下落过程，则返回
            if (canMove == false)
                return false;

            x2 = event.getX();
            y2 = event.getY();

            float tx = fallingBlock.getX();
            float ty = fallingBlock.getY();

            if (x1 - x2 > 50) {
                //向左滑动
                fallingBlock.move(-1);

                //更新界面
                father.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TetrisView.this.invalidate();
                    }
                });
            } else if (x2 - x1 > 50) {
                //向右滑动
                fallingBlock.move(1);

                //更新界面
                father.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TetrisView.this.invalidate();
                    }
                });
            } else if (y1 - y2 > 50) {
                //向上滑动，旋转俄罗斯方块
                fallingBlock.rotate();

                //更新界面
                father.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TetrisView.this.invalidate();
                    }
                });
            }
        }
        return true;
    }

    public void init() {
        /*
         * 游戏设置初始化
         */
        dropSpeed = 300;     //俄罗斯方块下落线程默认休眠时间

        if (difficultyType == 1)
            currentSpeed = 300;  //俄罗斯方块下落线程当前休眠时间
        else
            currentSpeed = 100;

        Arrays.fill(map, 0); //每行网格中包含俄罗斯方块单元的个数全部初始化为0

        flag = true;         //第一次进入线程循环

        isRun = true;        //游戏正在运行

        // 游戏主线程
        dropThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRun) {
                    if (flag) {
                        try {
                            //初始化各参数
                            Thread.sleep(1000);
                            h = getHeight();
                            w = getWidth();
                            beginX = (int) ((w - beginPoint) / 100) * 50 + beginPoint;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        //第一次进入线程循环，创建第一个俄罗斯方块
                        father.nextBlockView.createNextBlock();

                        father.runOnUiThread(new Runnable() {
                            //更新ui
                            @Override
                            public void run() {
                                Message msg = new Message();
                                msg.what = 2;
                                father.handler.sendMessage(msg);
                            }
                        });
                        flag = false; //下次循环不在执行此块操作
                    }
                    if (thread.getState() == Thread.State.TERMINATED || thread.getState() == Thread.State.NEW) {
                        //如果线程新创建或者已经结束，则重新创建新线程

                        thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                h = getHeight();
                                w = getWidth();

                                fallingBlock = father.nextBlockView.nextBlock; //跟新当前线程

                                father.nextBlockView.createNextBlock();        //创建新的后备线程

                                father.runOnUiThread(new Runnable() {
                                    //更新ui
                                    @Override
                                    public void run() {
                                        Message msg = new Message();
                                        msg.what = 3;
                                        father.handler.sendMessage(msg);
                                    }
                                });

                                fallingBlock.setBlocks(blocks);      //设置全局俄罗斯方块

                                float ty;

                                int end = (int) ((h - 50 - beginPoint) / BlockUnit.UNITSIZE);

                                float dropCount = fallingBlock.getY(); //用以记录正常下落情况下y坐标的值，即不考虑碰撞情况

                                canMove = true; //俄罗斯方块开始下落

                                while (dropCount - fallingBlock.getY() <= BlockUnit.UNITSIZE) {
                                    //若dropCount即y坐标的理想值与y坐标的准确值相差不到两个方块的大小，
                                    // 说明俄罗斯方块仍在下落，否则说明发生触碰事件，停止下落，跳出循环
                                    if (!father.isPause) {
                                        try {
                                            if (!father.isBottom)
                                                Thread.sleep(currentSpeed);

                                            //更新相应坐标值
                                            ty = fallingBlock.getY();
                                            ty = ty + BlockUnit.UNITSIZE;
                                            dropCount += BlockUnit.UNITSIZE;
                                            fallingBlock.setY(ty);

                                            father.runOnUiThread(new Runnable() {
                                                //更新ui
                                                @Override
                                                public void run() {
                                                    Message msg = new Message();
                                                    msg.what = 4;
                                                    father.handler.sendMessage(msg);
                                                }
                                            });
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                canMove = false;//俄罗斯方块结束下落
                                father.isBottom = false;

                                blocks.add(fallingBlock); //将俄罗斯方块加入静止的俄罗斯方块数组中

                                TetrisBlock temp = fallingBlock;

                                for (BlockUnit u : temp.getUnits()) {
                                    //更新map，即更新每行网格中静止俄罗斯方块单元的个数
                                    int index = (int) ((u.getY() - beginPoint) / 50); //计算所在行数
                                    if (index > 0) {
                                        map[index]++;
                                    }
                                }

                                //每行最大个数  ， 记录每次清除的行数
                                int full = (int) ((w - beginPoint) / BlockUnit.UNITSIZE), count = 0;

                                for (int i = 0; i <= end; i++) {
                                    if (map[i] >= full) {
                                        //记录消去行数
                                        count++;

                                        //将被消去行上的所有俄罗斯方块向下移动一行
                                        map[i] = 0;
                                        for (int j = i; j > 0; j--)
                                            map[j] = map[j - 1];
                                        map[0] = 0;

                                        //消去此行
                                        for (TetrisBlock b : blocks)
                                            b.remove(i);
                                        for (int j = blocks.size() - 1; j >= 0; j--) {
                                            if (blocks.get(j).getUnits().isEmpty()) {
                                                blocks.remove(j);
                                                continue;
                                            }
                                            for (BlockUnit u : blocks.get(j).getUnits()) {
                                                if ((int) ((u.getY() - beginPoint) / 50) < i)
                                                    u.setY(u.getY() + BlockUnit.UNITSIZE);
                                            }
                                        }
                                    }
                                }
                                //根据消去行数统计得分
                                switch (count) {
                                    case 1:
                                        father.scoreValue += 50;
                                        break;
                                    case 2:
                                        father.scoreValue += 110;
                                        break;
                                    case 3:
                                        father.scoreValue += 180;
                                        break;
                                    case 4:
                                        father.scoreValue += 260;
                                        break;
                                }
                                if (currentSpeed > 100) {
                                    father.speedValue = father.scoreValue / 1000 + 1;
                                    father.levelValue = father.scoreValue / 1000 + 1;
                                    currentSpeed = 300 - father.speedValue * 20;
                                }
                                if (father.scoreValue > father.maxScoreValue) {
                                    father.maxScoreValue = father.scoreValue;
                                    if (difficultyType == 1) {
                                        FileControl.getInstance().saveFile("最高分:" + father.maxScoreValue, "easy", father);
                                    } else if (difficultyType == 2) {
                                        FileControl.getInstance().saveFile("最高分:" + father.maxScoreValue, "hard", father);
                                    }
                                }
                                father.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //更新ui
                                        Message msg = new Message();
                                        msg.what = 5;
                                        father.handler.sendMessage(msg);
                                    }
                                });

                                for (TetrisBlock b : blocks) {
                                    //判断游戏是否应该结束
                                    for (BlockUnit u : b.getUnits())
                                        if (u.getY() <= BlockUnit.UNITSIZE) {
                                            //Game over
                                            isRun = false;
                                        }
                                }
                                if (!isRun) {
//                                    try {
//                                        dropThread.wait();
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//                                    }
                                    father.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            AlertDialog.Builder dialog = new AlertDialog.Builder(father);
                                            dialog.setTitle("游戏结束");
                                            dialog.setCancelable(false);
                                            dialog.setIcon(R.drawable.ic_launcher);
                                            dialog.setMessage("得分" + father.scoreValue);
                                            dialog.setNegativeButton("退出", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    Intent intent = new Intent(father, Start.class);
                                                    father.startActivity(intent);
                                                }
                                            });
                                            dialog.setPositiveButton("重新开始", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    clear();
                                                    init();
                                                }
                                            });
                                            if (!father.isFinishing())
                                                dialog.show();
                                        }
                                    });
                                }
                            }
                        });
                        thread.start();
                    }
                }
            }
        });
        dropThread.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        max_x = getWidth();
        max_y = getHeight();
        if (Start.NIGHT_flag) {
            canvas.drawColor(Color.BLACK);
        } else {
            canvas.drawColor(Color.WHITE);
        }
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        RectF rel;
        float size = BlockUnit.UNITSIZE;

        //俄罗斯方块颜色数组
        int color[] = {0, Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN, Color.GRAY};

        if (!blocks.isEmpty()) {
            //绘出所有静止的俄罗斯方块
            for (TetrisBlock block : blocks) {
                paint.setColor(color[block.getColor()]);          //设置画笔为俄罗斯方块的颜色
                for (BlockUnit u : block.getUnits()) {
                    float tx = u.getX();
                    float ty = u.getY();
                    rel = new RectF(tx, ty, tx + size, ty + size); //将方块画成圆角矩形的形式
                    canvas.drawRoundRect(rel, 8, 8, paint);
                }
            }
        }
        if (fallingBlock != null) {
            //绘制正在下落的俄罗斯方块
            paint.setColor(color[fallingBlock.getColor()]);
            for (BlockUnit u : fallingBlock.getUnits()) {
                float tx = u.getX();
                float ty = u.getY();
                rel = new RectF(tx, ty, tx + size, ty + size);
                canvas.drawRoundRect(rel, 8, 8, paint);
            }
        }
        if (Start.NIGHT_flag) {
            paint.setColor(Color.GRAY);
        } else {
            paint.setColor(Color.LTGRAY);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);

        //绘制网格
        for (int i = beginPoint; i < max_x - 50; i += 50) {
            for (int j = beginPoint; j < max_y - 50; j += 50) {
                rel = new RectF(i, j, i + 50, j + 50);
                canvas.drawRoundRect(rel, 8, 8, paint);
            }
        }
    }

    public TetrisBlock getFallingBlock() {
        return fallingBlock;
    }

    public float getH() {
        return h;
    }

    public void setH(float h) {
        this.h = h;
    }

    public void setFallingBlock(TetrisBlock fallingBlock) {
        this.fallingBlock = fallingBlock;
    }

    public Activity getFather() {
        return father;
    }

    public void setFather(Main father) {
        this.father = father;
    }

}
