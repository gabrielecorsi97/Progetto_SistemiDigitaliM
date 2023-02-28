import cv2
import numpy as np
import math
import time
from skimage.transform import hough_line, hough_line_peaks
from scipy.spatial import distance_matrix
from tabulate import tabulate
import os
from os.path import join

gaussianKernel = (3, 3)
dilateIterations = 1
kernelDilate = np.ones((2, 2), np.uint8)

def getLineFromPoint(rho, theta):
    a = np.cos(theta)
    b = np.sin(theta)
    x0 = a * rho
    y0 = b * rho
    x1 = int(x0 + 2000 * (-b))
    y1 = int(y0 + 2000 * (a))
    x2 = int(x0 - 2000 * (-b))
    y2 = int(y0 - 2000 * (a))
    return ((x1,y1),(x2,y2))


def findIntersection(x1, y1, x2, y2, x3, y3, x4, y4):
    px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / (
                (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4))
    py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / (
                (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4))
    return round(px), round(py)


def drawLinesVertical(pointsDown, pointsUp, img):

    for pd,pu in zip(pointsDown, pointsUp):
        img = cv2.line(img, pd, pu, (255, 255, 0), thickness=1)
    return img


def drawLinesHorizontal(pointsRight, pointsLeft, img):

    for pr,pl in zip(pointsRight, pointsLeft):
        img = cv2.line(img, pr, pl, (255, 255, 0), thickness=1)
    return img



def getLeftRightUpDownPoints(top9H,top9V, img):
    lines2pointsHorizontal = []
    for line in top9H:
        theta,rho = line
        ((x1, y1), (x2, y2)) = getLineFromPoint(rho, theta)
        lines2pointsHorizontal.append(((x1, y1), (x2, y2)))

    lines2pointsVertical = []
    for line in top9V:
        theta,rho = line
        ((x1, y1), (x2, y2)) = getLineFromPoint(rho, theta)
        lines2pointsVertical.append(((x1, y1), (x2, y2)))

    mintop9V = min(top9V, key=lambda x: abs(x[1]))
    minLineTop9V = getLineFromPoint(mintop9V[1], mintop9V[0])
    maxtop9V = max(top9V, key=lambda x: abs(x[1]))
    maxLineTop9V = getLineFromPoint(maxtop9V[1], maxtop9V[0])

    mintop9H = min(top9H, key=lambda x: abs(x[1]))
    minLineTop9H = getLineFromPoint(mintop9H[1], mintop9H[0])
    maxtop9H = max(top9H, key=lambda x: abs(x[1]))
    maxLineTop9H = getLineFromPoint(maxtop9H[1], maxtop9H[0])

    leftPoints = []
    rightPoints = []

    for line in lines2pointsHorizontal:
        leftPoint = findIntersection(minLineTop9V[0][0], minLineTop9V[0][1], minLineTop9V[1][0],
                                     minLineTop9V[1][1], line[0][0], line[0][1], line[1][0], line[1][1])
        rightPoint = findIntersection(maxLineTop9V[0][0], maxLineTop9V[0][1], maxLineTop9V[1][0],
                                      maxLineTop9V[1][1], line[0][0], line[0][1], line[1][0], line[1][1])
        leftPoints.append(leftPoint)
        rightPoints.append(rightPoint)
        #img = cv2.circle(img, leftPoint, radius=2, color=(0, 0, 255), thickness=-1)
        #img = cv2.circle(img, rightPoint, radius=2, color=(0, 0, 255), thickness=-1)


    upPoints = []
    downPoints = []
    for line in lines2pointsVertical:
        upPoint = findIntersection(minLineTop9H[0][0], minLineTop9H[0][1], minLineTop9H[1][0],
                                   minLineTop9H[1][1], line[0][0], line[0][1], line[1][0], line[1][1])
        downPoint = findIntersection(maxLineTop9H[0][0], maxLineTop9H[0][1], maxLineTop9H[1][0],
                                     maxLineTop9H[1][1], line[0][0], line[0][1], line[1][0], line[1][1])
        upPoints.append(upPoint)
        downPoints.append(downPoint)
        #img = cv2.circle(img, upPoint, radius=2, color=(0, 0, 255), thickness=-1)
        #img = cv2.circle(img, downPoint, radius=2, color=(0, 0, 255), thickness=-1)


    leftPoints.sort(key=lambda x: x[1])
    rightPoints.sort(key=lambda x: x[1])
    upPoints.sort(key=lambda x: x[0])
    downPoints.sort(key=lambda x: x[0])
    return leftPoints, rightPoints, upPoints, downPoints


def drawOutliers(outliersTop9H,outliersTop9V, debugLine):

    for line in outliersTop9H:
        theta,rho  = line
        ((x1, y1), (x2, y2)) = getLineFromPoint(rho, theta)
        debugLine = cv2.line(debugLine, (x1, y1), (x2, y2), (255, 0, 204), thickness=1) #fucsia

    for line in outliersTop9V:
        theta, rho = line
        ((x1, y1), (x2, y2)) = getLineFromPoint(rho, theta)
        debugLine = cv2.line(debugLine, (x1, y1), (x2, y2), (51, 255, 102), thickness=1) #verde
    return debugLine


def filterCenter(img, input_image):
    mask = np.full((input_image.shape[0],input_image.shape[0]), 0).astype(np.uint8)
    points = np.array([[round(121 * input_image.shape[0] / 640), round(171 * input_image.shape[0] / 640)],
                       [round(50 * input_image.shape[0] / 640), round(505 * input_image.shape[0] / 640)],
                       [round(589 * input_image.shape[0] / 640), round(505 * input_image.shape[0] / 640)],
                       [round(517 * input_image.shape[0] / 640), round(171 * input_image.shape[0] / 640)]])
    cv2.fillPoly(mask, pts=[points], color=(255, 255, 255))
    img = np.where(mask.astype(bool), img, 0)
    return img


def filter_horizontal_lines(horizontal_lines, vertical_lines,debug):
    indexMiddlePoint = round(len(horizontal_lines) / 2.0) - 1
    v_line = vertical_lines[indexMiddlePoint] #seleziono la linea verticale nel mezzo
    theta_v, rho_v = v_line
    ((x3, y3), (x4, y4)) = getLineFromPoint(rho_v, theta_v)
    horizontal_points = []
    for line in horizontal_lines:
        theta, rho = line
        ((x1, y1), (x2, y2)) = getLineFromPoint(rho, theta)
        horizontal_points.append(findIntersection(x1, y1, x2, y2, x3, y3, x4, y4)) #calcolo tutte le intersezioni tra la linea verticale scelta prima
                                                                                   #e tutte le righe orizzontali
    distance_matrix_horizontal = distance_matrix(horizontal_points, horizontal_points)

    if debug:
        filenameMatrixH = join(os.environ["HOME"], "distanceMatrxiHorizontal.txt")
        filenameMatrixOBJH = join(os.environ["HOME"], "distanceMatrxiHorizontal.npy")
        with open(filenameMatrixH, 'w') as f:
            f.write(tabulate(distance_matrix_horizontal,horizontal_points,tablefmt="fancy_grid",floatfmt=".2f",showindex=horizontal_points))
            f.flush()
        with open(filenameMatrixOBJH, 'wb') as f:
            np.save(f, distance_matrix_horizontal)

    indexMiddlePoint = round(len(horizontal_lines) / 2.0) - 1
    ERROR_L = 0
    ERROR_H = 0.05
    result = []
    firstIterationTODO = True
    numIteration = 0
    while firstIterationTODO or (len(result) < 9 and numIteration<5):
        numIteration = numIteration +1
        ERROR_L = ERROR_L + 0.02
        ERROR_H = ERROR_H + 0.06
        result = []
        outliers9H = []
        result.append([horizontal_lines[indexMiddlePoint], 0])
        result.append([horizontal_lines[indexMiddlePoint + 1], 0])
        result.append([horizontal_lines[indexMiddlePoint - 1], 0])
        oldPointIndexL = indexMiddlePoint - 1
        newPointIndexL = indexMiddlePoint - 2
        oldPointIndexH = indexMiddlePoint + 1
        newPointIndexH = indexMiddlePoint + 2
        noHigher = False
        noLower = False
        index = 2
        distLow = distance_matrix_horizontal[indexMiddlePoint - 1][indexMiddlePoint]
        distHigh = distance_matrix_horizontal[indexMiddlePoint + 1][indexMiddlePoint]
        while not (noLower and noHigher):
            index = index + 1
            if indexMiddlePoint - index >= -1:
                DIFF = abs(distance_matrix_horizontal[oldPointIndexL][indexMiddlePoint] - distance_matrix_horizontal[newPointIndexL][indexMiddlePoint])
                if not noLower and (distLow - distLow * ERROR_L) < DIFF < (distLow + distLow * ERROR_H):
                    result.append([horizontal_lines[newPointIndexL], min([DIFF - (distLow - distLow * ERROR_L),(distLow + distLow * ERROR_H) - DIFF])])
                    distLow = DIFF
                    oldPointIndexL = indexMiddlePoint - index + 1
                    newPointIndexL = indexMiddlePoint - index
                else:
                    outliers9H.append(horizontal_lines[newPointIndexL])
                    newPointIndexL = indexMiddlePoint - index
            else:
                noLower = True

            if indexMiddlePoint + index <= len(horizontal_lines):
                DIFF = abs(distance_matrix_horizontal[oldPointIndexH][indexMiddlePoint] - distance_matrix_horizontal[newPointIndexH][indexMiddlePoint])
                if not noHigher and (distHigh - distHigh * ERROR_H) < DIFF < (distHigh + distHigh * ERROR_L):
                    result.append([horizontal_lines[newPointIndexH],
                                   min([DIFF - (distHigh - distHigh * ERROR_H),  (distHigh + distHigh * ERROR_L) - DIFF])])

                    distHigh = DIFF
                    oldPointIndexH = indexMiddlePoint + index - 1
                    newPointIndexH = indexMiddlePoint + index
                else:
                    outliers9H.append(horizontal_lines[newPointIndexH])
                    newPointIndexH = indexMiddlePoint + index
            else:
                noHigher = True

        result.sort(key=lambda x: x[0][1])
        outliers9H.sort(key=lambda x: x[1])

        firstIterationTODO = False
    if len(result) == 9:
        result9H = [point[0] for point in result]
        print("Number iteration filter horizontal: {}".format(numIteration))
        return result9H, outliers9H

    while len(result) > 9:
        if result[0][1] > result[-1][1]:
            outliers9H.append(result[-1][0])
            result = result[:9]
        else:
            outliers9H.append(result.pop(0)[0])
    result9H = [point[0] for point in result]
    print("Number iteration filter horizontal: {}".format(numIteration))
    return result9H, outliers9H

def filter_vertical_lines(vertical_lines, horizontal_lines, debug):
    indexMiddlePoint = round(len(horizontal_lines) / 2.0) - 1
    h_line = horizontal_lines[indexMiddlePoint]
    theta_h, rho_h = h_line
    ((x3, y3), (x4, y4)) = getLineFromPoint(rho_h, theta_h)
    vertical_points = []
    for line in vertical_lines:
        theta, rho = line
        ((x1, y1), (x2, y2)) = getLineFromPoint(rho, theta)
        vertical_points.append(findIntersection(x1, y1, x2, y2,x3, y3, x4, y4))
    distance_matrix_vertical = distance_matrix(vertical_points, vertical_points)

    if debug:
        filenameMatrixV = join(os.environ["HOME"], "distanceMatrxiVertical.txt")
        filenameMatrixOBJV = join(os.environ["HOME"], "distanceMatrxiVertical.npy")
        with open(filenameMatrixV, 'w') as f:
            f.write(tabulate(distance_matrix_vertical,vertical_points,tablefmt="fancy_grid",floatfmt=".2f",showindex=vertical_points))
            f.flush()
        with open(filenameMatrixOBJV, 'wb') as f:
            np.save(f, distance_matrix_vertical)

    indexMiddlePoint = round(len(vertical_lines) / 2.0) - 1
    ERROR = 0.05
    result = []
    firstIterationTODO = True
    numIteration = 0
    while firstIterationTODO or (len(result) < 9 and numIteration<5):
        numIteration = numIteration +1
        ERROR = ERROR + 0.035
        result = []
        outliers9V = []
        result.append([vertical_lines[indexMiddlePoint], 0])
        result.append([vertical_lines[indexMiddlePoint + 1], 0])
        result.append([vertical_lines[indexMiddlePoint - 1], 0])
        oldPointIndexL = indexMiddlePoint - 1
        newPointIndexL = indexMiddlePoint - 2
        oldPointIndexH = indexMiddlePoint + 1
        newPointIndexH = indexMiddlePoint + 2
        noHigher = False
        noLower = False
        index = 2
        distLow = distance_matrix_vertical[indexMiddlePoint - 1][indexMiddlePoint]
        distHigh = distance_matrix_vertical[indexMiddlePoint + 1][indexMiddlePoint]
        while not (noLower and noHigher):
            index = index + 1
            if indexMiddlePoint - index >= -1:
                DIFF= abs(distance_matrix_vertical[oldPointIndexL][indexMiddlePoint] - distance_matrix_vertical[newPointIndexL][indexMiddlePoint])
                if not noLower and (distLow + distLow * ERROR) > DIFF > (distLow - distLow * ERROR):
                    result.append([vertical_lines[newPointIndexL],min([DIFF - (distLow - distLow * ERROR),(distLow + distLow * ERROR) - DIFF])])
                    distLow = DIFF
                    oldPointIndexL = indexMiddlePoint - index + 1
                    newPointIndexL = indexMiddlePoint - index
                else:
                    outliers9V.append(vertical_lines[newPointIndexL])
                    newPointIndexL = indexMiddlePoint - index
            else:
                noLower = True

            if indexMiddlePoint + index <= len(vertical_lines):
                DIFF = abs(distance_matrix_vertical[oldPointIndexH][indexMiddlePoint] - distance_matrix_vertical[newPointIndexH][indexMiddlePoint])
                if not noHigher and (distHigh - distHigh * ERROR) < DIFF < (distHigh + distHigh * ERROR):
                    result.append([vertical_lines[newPointIndexH], min([DIFF - (distHigh - distHigh * ERROR), (distHigh + distHigh * ERROR) - DIFF])])
                    distHigh = DIFF
                    oldPointIndexH = indexMiddlePoint + index - 1
                    newPointIndexH = indexMiddlePoint + index
                else:
                    outliers9V.append(vertical_lines[newPointIndexH])
                    newPointIndexH = indexMiddlePoint + index
            else:
                noHigher = True

        result.sort(key=lambda x: x[0][1])
        outliers9V.sort(key=lambda x: x[1])
        firstIterationTODO = False

    if len(result) == 9:
        result9V = [point[0] for point in result]
        print("Number iteration filter vertical: {}".format(numIteration))
        return result9V, outliers9V

    while len(result) > 9:
        if result[0][1] > result[-1][1]:
            outliers9V.append(result[-1][0])
            result = result[:9]
        else:
            outliers9V.append(result.pop(0)[0])
    result9V = [point[0] for point in result]
    print("Number iteration filter vertical: {}".format(numIteration))
    return result9V, outliers9V


dim = (640,640)
dimh = 480
def main(image, width, height, debug):

    ba = bytearray(image)
    npArray = np.frombuffer(ba, dtype = np.uint8)
    npArray = npArray.reshape(height, width ,4)
    npArray = npArray[:, round((width-height)/2):height+round((width-height)/2),:]
    npArray = npArray[:,:,:3]
    inputImg = cv2.cvtColor(npArray, cv2.COLOR_BGR2RGB)
    imgRotated = cv2.rotate(inputImg, cv2.ROTATE_90_CLOCKWISE)
    #imgRotated = cv2.resize(imgRotated, dim)
    img_grayscale = cv2.cvtColor(imgRotated, cv2.COLOR_BGR2GRAY)
    mean = np.mean(img_grayscale)

    img_blur = cv2.GaussianBlur(img_grayscale, gaussianKernel, sigmaX=0, sigmaY=0)
    edgesBlurredCanned = cv2.Canny(img_blur, 0.26*mean,4*mean)
    edgesBlurredCanned = filterCenter(edgesBlurredCanned, inputImg)
    edgesBlurredCanned = cv2.dilate(edgesBlurredCanned, kernelDilate, iterations=dilateIterations)


    if debug:
        filename1 = join(os.environ["HOME"], "inputImg.jpg")
        filename2 = join(os.environ["HOME"], "imgRotatedResized.jpg")
        filename3 = join(os.environ["HOME"], "edgesBlurredMasked.jpg")
        cv2.imwrite(filename1, inputImg)
        cv2.imwrite(filename2, imgRotated)
        cv2.imwrite(filename3, edgesBlurredCanned)

    startHough2 = time.time_ns()
    ANGLE_TESTED = 180
    tested_angles = np.linspace(-np.pi / 2, np.pi / 2, ANGLE_TESTED, endpoint=False)
    h, theta, d = hough_line(edgesBlurredCanned, theta=tested_angles)

    print("Hough2 transform done in: ", (time.time_ns()-startHough2)/1000000000)

    horizontal_lines = []
    vertical_lines = []
    h_vertical = h[:, 55:125]
    theta_vertical = theta[55:125]
    h_horizontal = np.concatenate((np.flip(h[:, 160:180], axis=0), h[:, 0:20]), axis=1)
    theta_horizontal = np.concatenate([theta[160:180], theta[0:20]], axis=0)

    startPeaks = time.time_ns()
    threshold_v = (0.35*np.max(h_vertical))
    for _, angle, dist in zip(*hough_line_peaks(h_vertical, theta_vertical, d, min_distance=8, min_angle=1, threshold=threshold_v, num_peaks=12)):
        if 0 <= abs(angle) <= 0.50:
            vertical_lines.append([angle, dist])

    threshold_h = (0.35* np.max(h_horizontal))
    for _, angle, dist in zip(*hough_line_peaks(h_horizontal, theta_horizontal, d, min_distance=8, min_angle=1, threshold=threshold_h, num_peaks=12)):
        if 1.45 <= abs(angle) <= 1.68:
            if angle>0:
                dist = -dist
            horizontal_lines.append([angle, dist])

    print("Peaks function done in: ", (time.time_ns()-startPeaks)/1000000000)


    horizontal_lines.sort(key=lambda x: abs(x[1]), reverse=True)
    vertical_lines.sort(key=lambda x: abs(x[1]), reverse=True)
    print("Number horizontal lines: {}".format(len(horizontal_lines)))
    print("Number vertical lines: {}".format(len(vertical_lines)))

    if len(horizontal_lines)<9 or len(vertical_lines)<9:
        return (-1).to_bytes(4,"big", signed=True)

    startFilter = time.time_ns()

    outliers_vertical = []
    outliers_horizontal = []
    if len(vertical_lines)>9:
        filtered_vertical_lines, outliers_vertical = filter_vertical_lines(vertical_lines, horizontal_lines, debug)
    else:
        filtered_vertical_lines = vertical_lines
    if len(horizontal_lines)>9:
        filtered_horizontal_lines, outliers_horizontal = filter_horizontal_lines(horizontal_lines, vertical_lines, debug)
    else:
        filtered_horizontal_lines = horizontal_lines
    outliersTop9H = np.asarray(outliers_horizontal)
    outliersTop9V = np.asarray(outliers_vertical)
    top9V = np.asarray(filtered_vertical_lines)
    top9H = np.asarray(filtered_horizontal_lines)
    print("Number horizontal lines: {} Number horizontal outliers: {}".format(len(top9H),len(outliersTop9H)))
    print("Number vertical lines: {} Number vertical outliers: {}".format(len(top9V),len(outliersTop9V)))
    print("Filter done in : ", (time.time_ns()-startFilter)/1000000000)

    if len(top9H)!=9 or len(top9V)!=9:
        return (-1).to_bytes(4,"big", signed=True)

        #coordinatesLineHorizontalRadians = [(rho - round(fast_rhos[-1]), np.radians(theta + 45)) for theta, rho in
        #                                       top9H]
        #coordinatesLineVerticalRadians = [(rho - round(fast_rhos[-1]), np.radians(theta - 45)) for theta, rho  in
        #                                      top9V]

    startPoints = time.time_ns()
    leftPoints, rightPoints, upPoints, downPoints = getLeftRightUpDownPoints(top9H,top9V, imgRotated)
    print("Points function done in: ", (time.time_ns()-startPoints)/1_000_000_000)

    if debug:
        filename1 = join(os.environ["HOME"], "inputImg.jpg")
        filename2 = join(os.environ["HOME"], "imgRotatedResized.jpg")
        filename3 = join(os.environ["HOME"], "edgesBlurredMasked.jpg")
        cv2.imwrite(filename1, inputImg)
        cv2.imwrite(filename2, imgRotated)
        cv2.imwrite(filename3, edgesBlurredCanned)
        filename5 = join(os.environ["HOME"], "accumulatorHorizontalRGB.jpg")
        filename6 = join(os.environ["HOME"], "accumulatorVerticalRGB.jpg")
        accumulatorHorizontalRGB = cv2.cvtColor(h_horizontal.astype(np.uint8),cv2.COLOR_GRAY2RGB)
        accumulatorVerticalRGB = cv2.cvtColor(h_vertical.astype(np.uint8),cv2.COLOR_GRAY2RGB)

        for point in horizontal_lines:
            thispoint = point.copy()
            if thispoint[0] > 0: thispoint[1] = -thispoint[1]
            accumulatorHorizontalRGB = cv2.circle(accumulatorHorizontalRGB,
                                                  (round(np.degrees(abs(thispoint[0]))) - 45,round( d[-1]+thispoint[1])), radius=2,
                                                  color=(0, 0, 255), thickness=-1)
        for point in vertical_lines:
            accumulatorVerticalRGB = cv2.circle(accumulatorVerticalRGB, (round(np.degrees(point[0]))+45, round(point[1]+d[-1])), radius=2, color=(0, 0, 255), thickness=-1)

        cv2.imwrite(filename5, accumulatorHorizontalRGB)
        cv2.imwrite(filename6, accumulatorVerticalRGB)
        filename9 = join(os.environ["HOME"], "ransacHorizontalBest9.jpg")
        filename10 = join(os.environ["HOME"], "ransacVerticalBest9.jpg")
        for point in top9H:
            thispoint = point.copy()
            if thispoint[0] > 0: thispoint[1] = -thispoint[1]
            accumulatorHorizontalRGB = cv2.circle(accumulatorHorizontalRGB, (round(np.degrees(abs(thispoint[0]))) - 45,round( d[-1]+thispoint[1])), radius=2, color=(0, 255, 0), thickness=-1)
        for point in top9V:
            accumulatorVerticalRGB = cv2.circle(accumulatorVerticalRGB, (round(np.degrees(point[0]))+45, round(point[1]+d[-1])), radius=2, color=(0, 255, 0), thickness=-1)
        for point in outliersTop9H:
            thispoint = point.copy()
            if thispoint[0] > 0: thispoint[1] = -thispoint[1]
            accumulatorHorizontalRGB = cv2.circle(accumulatorHorizontalRGB, (round(np.degrees(abs(thispoint[0]))) - 45,round( d[-1]+thispoint[1])), radius=2, color=(255,102, 0), thickness=-1)
        for point in outliersTop9V:
            accumulatorVerticalRGB = cv2.circle(accumulatorVerticalRGB, (round(np.degrees(point[0]))+45, round(point[1]+d[-1])), radius=2, color=(255,102, 0), thickness=-1)
        cv2.imwrite(filename9, accumulatorHorizontalRGB)
        cv2.imwrite(filename10, accumulatorVerticalRGB)
        debugLine = drawLinesHorizontal(rightPoints, leftPoints, imgRotated.copy())
        debugLine = drawLinesVertical(downPoints, upPoints, debugLine)
        debugLine = drawOutliers(outliersTop9H,
                                outliersTop9V, debugLine)
        filenamedebugLine = join(os.environ["HOME"], "debugLine.jpg")
        cv2.imwrite(filenamedebugLine, debugLine)


    if(len(leftPoints) != 9 or len(rightPoints) != 9 or len(upPoints) != 9 or len(downPoints) != 9):
        return (-1).to_bytes(4,"big", signed=True)


    leftPointsFlattened = [item for sublist in leftPoints for item in sublist]
    rightPointsFlattened = [item for sublist in rightPoints for item in sublist]
    upPointsFlattened = [item for sublist in upPoints for item in sublist]
    downPointsFlattened = [item for sublist in downPoints for item in sublist]

    leftPointsBytes = bytearray()
    rightPointsBytes = bytearray()
    upPointsBytes = bytearray()
    downPointsBytes = bytearray()
    for val1,val2,val3,val4 in zip(leftPointsFlattened,rightPointsFlattened,upPointsFlattened,downPointsFlattened):
        leftPointsBytes.extend(val1.to_bytes(4,"big",signed=True))
        rightPointsBytes.extend(val2.to_bytes(4,"big",signed=True))
        upPointsBytes.extend(val3.to_bytes(4,"big",signed=True))
        downPointsBytes.extend(val4.to_bytes(4,"big",signed=True))
    return (1).to_bytes(4,"big")+leftPointsBytes+rightPointsBytes+upPointsBytes+downPointsBytes




