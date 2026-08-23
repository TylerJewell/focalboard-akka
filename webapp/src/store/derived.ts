// Copyright (c) 2015-present Mattermost, Inc. All Rights Reserved.
// See LICENSE.txt for license information.
//
// What the server says this view shows.
//
// In the original this is worked out here, in the browser, by `store/cards.ts` and
// `boardUtils.ts`: every open client re-derives the same answer from the same blocks. This
// port derives it once, on the board itself, and the interface asks for the answer. The
// selectors below are the only new state; the components reading them are unchanged except
// for where they read from.
import {createAsyncThunk, createSelector, createSlice, PayloadAction} from '@reduxjs/toolkit'

import {default as client} from '../octoClient'

import {RootState} from './index'

export type DerivedGroup = {
    optionId: string
    label: string
    color: string
    cardIds: string[]
}

export type DerivedView = {
    viewId: string
    orderedCardIds: string[]
    visible: DerivedGroup[]
    hidden: DerivedGroup[]
}

type DerivedState = {
    byView: {[key: string]: DerivedView}
    pending: boolean
}

export const refreshDerivedView = createAsyncThunk<DerivedView | null,
    {boardId: string, viewId: string, searchText: string}>(
        'refreshDerivedView',
        async ({boardId, viewId, searchText}) => {
            if (!boardId || !viewId) {
                return null
            }
            return client.getDerivedView(boardId, viewId, searchText)
        },
    )

const derivedSlice = createSlice({
    name: 'derived',
    initialState: {byView: {}, pending: false} as DerivedState,
    reducers: {
        clearDerived: (state) => {
            state.byView = {}
        },
    },
    extraReducers: (builder) => {
        builder.addCase(refreshDerivedView.pending, (state) => {
            state.pending = true
        })
        builder.addCase(refreshDerivedView.fulfilled, (state, action: PayloadAction<DerivedView | null>) => {
            state.pending = false
            if (action.payload) {
                state.byView[action.payload.viewId] = action.payload
            }
        })
        builder.addCase(refreshDerivedView.rejected, (state) => {
            state.pending = false
        })
    },
})

export const {clearDerived} = derivedSlice.actions
export const {reducer} = derivedSlice

export const getDerivedByView = (state: RootState): {[key: string]: DerivedView} => state.derived?.byView || {}

export const getCurrentDerivedView = createSelector(
    getDerivedByView,
    (state: RootState) => state.views.current,
    (byView, viewId) => byView[viewId],
)
